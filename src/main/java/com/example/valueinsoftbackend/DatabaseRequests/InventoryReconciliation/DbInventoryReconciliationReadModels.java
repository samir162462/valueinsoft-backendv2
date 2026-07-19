package com.example.valueinsoftbackend.DatabaseRequests.InventoryReconciliation;

import com.example.valueinsoftbackend.Model.Inventory.InventoryReconciliationSnapshot;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Repository
public class DbInventoryReconciliationReadModels {

    private final NamedParameterJdbcTemplate jdbc;

    public DbInventoryReconciliationReadModels(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<String> missingObjects(int companyId) {
        return jdbc.queryForList(
                """
                SELECT missing_object
                FROM public.inventory_tenant_schema_drift()
                WHERE company_id = :companyId
                ORDER BY missing_object
                """,
                new MapSqlParameterSource("companyId", companyId),
                String.class
        );
    }

    public InventoryReconciliationSnapshot.Summary summary(int companyId, int branchId) {
        String sql = reconciliationCtes(companyId) + """
                SELECT
                    COUNT(*) AS product_keys,
                    COUNT(*) FILTER (WHERE balance_vs_legacy_delta <> 0) AS legacy_differences,
                    COUNT(*) FILTER (WHERE balance_vs_movement_delta <> 0) AS movement_differences,
                    COUNT(*) FILTER (WHERE unit_total > 0 AND balance_vs_units_delta <> 0) AS unit_differences,
                    COUNT(*) FILTER (
                        WHERE balance_vs_legacy_delta <> 0
                           OR balance_vs_movement_delta <> 0
                           OR (unit_total > 0 AND balance_vs_units_delta <> 0)
                    ) AS discrepancy_rows
                FROM classified
                """;

        return jdbc.queryForObject(sql, params(companyId, branchId), (rs, rowNum) ->
                new InventoryReconciliationSnapshot.Summary(
                        rs.getLong("product_keys"),
                        rs.getLong("legacy_differences"),
                        rs.getLong("movement_differences"),
                        rs.getLong("unit_differences"),
                        rs.getLong("discrepancy_rows")
                ));
    }

    public List<InventoryReconciliationSnapshot.Row> discrepancies(int companyId, int branchId, int limit) {
        String sql = reconciliationCtes(companyId) + """
                SELECT *
                FROM classified
                WHERE balance_vs_legacy_delta <> 0
                   OR balance_vs_movement_delta <> 0
                   OR (unit_total > 0 AND balance_vs_units_delta <> 0)
                ORDER BY product_id
                LIMIT :limit
                """;

        return jdbc.query(sql, params(companyId, branchId).addValue("limit", limit), (rs, rowNum) -> {
            BigDecimal legacyDelta = rs.getBigDecimal("balance_vs_legacy_delta");
            BigDecimal movementDelta = rs.getBigDecimal("balance_vs_movement_delta");
            BigDecimal unitDelta = rs.getBigDecimal("balance_vs_units_delta");
            long unitTotal = rs.getLong("unit_total");
            ArrayList<String> types = new ArrayList<>(3);
            if (legacyDelta.signum() != 0) types.add("BALANCE_VS_LEGACY_LEDGER");
            if (movementDelta.signum() != 0) types.add("BALANCE_VS_MODERN_MOVEMENT");
            if (unitTotal > 0 && unitDelta.signum() != 0) types.add("BALANCE_VS_SERIALIZED_UNITS");

            return new InventoryReconciliationSnapshot.Row(
                    rs.getLong("product_id"),
                    rs.getString("product_name"),
                    rs.getBigDecimal("balance_quantity"),
                    rs.getBigDecimal("reserved_quantity"),
                    rs.getBigDecimal("legacy_ledger_quantity"),
                    rs.getBigDecimal("modern_movement_quantity"),
                    rs.getLong("available_units"),
                    rs.getLong("reserved_units"),
                    rs.getLong("other_units"),
                    legacyDelta,
                    movementDelta,
                    unitDelta,
                    List.copyOf(types)
            );
        });
    }

    private MapSqlParameterSource params(int companyId, int branchId) {
        return new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("branchId", branchId);
    }

    private String reconciliationCtes(int companyId) {
        String balance = TenantSqlIdentifiers.inventoryBranchStockBalanceTable(companyId);
        String ledger = TenantSqlIdentifiers.inventoryStockLedgerTable(companyId);
        String movement = TenantSqlIdentifiers.inventoryStockMovementTable(companyId);
        String units = TenantSqlIdentifiers.inventoryProductUnitTable(companyId);
        String products = TenantSqlIdentifiers.inventoryProductTable(companyId);

        return """
                WITH product_keys AS (
                    SELECT product_id FROM %s WHERE company_id = :companyId AND branch_id = :branchId
                    UNION
                    SELECT product_id FROM %s WHERE company_id = :companyId AND branch_id = :branchId
                    UNION
                    SELECT product_id FROM %s WHERE company_id = :companyId AND branch_id = :branchId
                    UNION
                    SELECT product_id FROM %s WHERE company_id = :companyId AND branch_id = :branchId
                ),
                balances AS (
                    SELECT product_id, quantity::numeric AS quantity, reserved_qty::numeric AS reserved_qty
                    FROM %s
                    WHERE company_id = :companyId AND branch_id = :branchId
                ),
                legacy_ledger AS (
                    SELECT product_id, COALESCE(SUM(quantity_delta), 0)::numeric AS quantity
                    FROM %s
                    WHERE company_id = :companyId AND branch_id = :branchId
                    GROUP BY product_id
                ),
                modern_movement AS (
                    SELECT product_id, COALESCE(SUM(quantity_delta), 0)::numeric AS quantity
                    FROM %s
                    WHERE company_id = :companyId AND branch_id = :branchId
                    GROUP BY product_id
                ),
                unit_counts AS (
                    SELECT product_id,
                           COUNT(*) FILTER (WHERE status = 'AVAILABLE') AS available_units,
                           COUNT(*) FILTER (WHERE status = 'RESERVED') AS reserved_units,
                           COUNT(*) FILTER (WHERE status NOT IN ('AVAILABLE', 'RESERVED')) AS other_units,
                           COUNT(*) AS unit_total
                    FROM %s
                    WHERE company_id = :companyId AND branch_id = :branchId
                    GROUP BY product_id
                ),
                classified AS (
                    SELECT keys.product_id,
                           product.product_name,
                           COALESCE(balance.quantity, 0)::numeric AS balance_quantity,
                           COALESCE(balance.reserved_qty, 0)::numeric AS reserved_quantity,
                           COALESCE(legacy.quantity, 0)::numeric AS legacy_ledger_quantity,
                           COALESCE(movement.quantity, 0)::numeric AS modern_movement_quantity,
                           COALESCE(unit_count.available_units, 0) AS available_units,
                           COALESCE(unit_count.reserved_units, 0) AS reserved_units,
                           COALESCE(unit_count.other_units, 0) AS other_units,
                           COALESCE(unit_count.unit_total, 0) AS unit_total,
                           (COALESCE(balance.quantity, 0) - COALESCE(legacy.quantity, 0))::numeric AS balance_vs_legacy_delta,
                           (COALESCE(balance.quantity, 0) - COALESCE(movement.quantity, 0))::numeric AS balance_vs_movement_delta,
                           (COALESCE(balance.quantity, 0) - COALESCE(unit_count.available_units, 0) - COALESCE(unit_count.reserved_units, 0))::numeric AS balance_vs_units_delta
                    FROM product_keys keys
                    LEFT JOIN %s product ON product.product_id = keys.product_id
                    LEFT JOIN balances balance ON balance.product_id = keys.product_id
                    LEFT JOIN legacy_ledger legacy ON legacy.product_id = keys.product_id
                    LEFT JOIN modern_movement movement ON movement.product_id = keys.product_id
                    LEFT JOIN unit_counts unit_count ON unit_count.product_id = keys.product_id
                )
                """.formatted(balance, ledger, movement, units, balance, ledger, movement, units, products);
    }
}
