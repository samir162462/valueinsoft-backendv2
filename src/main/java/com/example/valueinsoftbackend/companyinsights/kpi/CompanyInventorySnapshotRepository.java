package com.example.valueinsoftbackend.companyinsights.kpi;

import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Builds and reads {@code public.company_inventory_snapshot} — a per-product, cross-branch
 * inventory rollup used by the COMPANY_WIDE_LOW_STOCK and DEAD_STOCK_COMPANY_WIDE rules.
 *
 * <p>The rebuild is set-based (one INSERT..SELECT per company) sourced from the company-wide
 * tenant inventory tables (no branch-suffixed tables involved), so it is cheap even for
 * companies with many branches.
 */
@Repository
public class CompanyInventorySnapshotRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public CompanyInventorySnapshotRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Rebuild the snapshot for a company/date. Idempotent upsert keyed by
     * (company_id, snapshot_date, product_id).
     */
    public int rebuildSnapshot(int companyId, LocalDate snapshotDate, int deadStockNoSaleDays) {
        String stockTable = TenantSqlIdentifiers.inventoryBranchStockBalanceTable(companyId);
        String productTable = TenantSqlIdentifiers.inventoryProductTable(companyId);
        String branchProductTable = TenantSqlIdentifiers.inventoryBranchProductTable(companyId);
        String ledgerTable = TenantSqlIdentifiers.inventoryStockLedgerTable(companyId);

        String sql = """
                INSERT INTO public.company_inventory_snapshot
                    (company_id, snapshot_date, product_id,
                     total_qty, total_value, branch_count_with_stock, branches_below_reorder,
                     branches_out_of_stock, last_sale_date, last_movement_date, is_dead_stock, computed_at)
                WITH stock AS (
                    SELECT s.product_id,
                           SUM(COALESCE(s.quantity, 0))::numeric AS total_qty,
                           SUM(COALESCE(s.quantity, 0) * COALESCE(p.buying_price, 0))::numeric AS total_value,
                           COUNT(*) FILTER (WHERE s.quantity > 0) AS branch_count_with_stock,
                           COUNT(*) FILTER (WHERE s.quantity <= 0) AS branches_out_of_stock,
                           COUNT(*) FILTER (WHERE ibp.reorder_level IS NOT NULL AND s.quantity <= ibp.reorder_level) AS branches_below_reorder
                    FROM %s s
                    JOIN %s p ON p.product_id = s.product_id
                    LEFT JOIN %s ibp ON ibp.product_id = s.product_id AND ibp.branch_id = s.branch_id
                    GROUP BY s.product_id
                ),
                mv AS (
                    SELECT product_id,
                           MAX(created_at)::date AS last_movement_date,
                           MAX(created_at) FILTER (
                               WHERE movement_type ILIKE '%%sale%%' OR movement_type ILIKE '%%sold%%'
                           )::date AS last_sale_date
                    FROM %s
                    GROUP BY product_id
                )
                SELECT :companyId, :snapshotDate, stock.product_id,
                       stock.total_qty, stock.total_value, stock.branch_count_with_stock, stock.branches_below_reorder,
                       stock.branches_out_of_stock, mv.last_sale_date, mv.last_movement_date,
                       (stock.total_qty > 0 AND (mv.last_movement_date IS NULL
                            OR mv.last_movement_date < (:snapshotDate::date - make_interval(days => :deadStockDays)))) AS is_dead_stock,
                       now()
                FROM stock
                LEFT JOIN mv ON mv.product_id = stock.product_id
                ON CONFLICT (company_id, snapshot_date, product_id) DO UPDATE SET
                     total_qty = EXCLUDED.total_qty,
                     total_value = EXCLUDED.total_value,
                     branch_count_with_stock = EXCLUDED.branch_count_with_stock,
                     branches_below_reorder = EXCLUDED.branches_below_reorder,
                     branches_out_of_stock = EXCLUDED.branches_out_of_stock,
                     last_sale_date = EXCLUDED.last_sale_date,
                     last_movement_date = EXCLUDED.last_movement_date,
                     is_dead_stock = EXCLUDED.is_dead_stock,
                     computed_at = now()
                """.formatted(stockTable, productTable, branchProductTable, ledgerTable);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("snapshotDate", snapshotDate)
                .addValue("deadStockDays", deadStockNoSaleDays);
        return jdbcTemplate.update(sql, params);
    }

    public List<InventorySnapshotRow> findLowStock(int companyId, LocalDate snapshotDate, int multiBranchCount) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("snapshotDate", snapshotDate)
                .addValue("multiBranchCount", multiBranchCount);
        return jdbcTemplate.query(
                """
                        SELECT product_id, total_qty, total_value, branch_count_with_stock,
                               branches_below_reorder, branches_out_of_stock, last_sale_date, last_movement_date, is_dead_stock
                        FROM public.company_inventory_snapshot
                        WHERE company_id = :companyId AND snapshot_date = :snapshotDate
                          AND (branches_below_reorder >= :multiBranchCount OR branches_out_of_stock > 0)
                        ORDER BY branches_out_of_stock DESC, branches_below_reorder DESC, total_value DESC
                        """,
                params, this::map);
    }

    public List<InventorySnapshotRow> findDeadStock(int companyId, LocalDate snapshotDate, java.math.BigDecimal minValue) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("snapshotDate", snapshotDate)
                .addValue("minValue", minValue);
        return jdbcTemplate.query(
                """
                        SELECT product_id, total_qty, total_value, branch_count_with_stock,
                               branches_below_reorder, branches_out_of_stock, last_sale_date, last_movement_date, is_dead_stock
                        FROM public.company_inventory_snapshot
                        WHERE company_id = :companyId AND snapshot_date = :snapshotDate
                          AND is_dead_stock = TRUE AND total_value >= :minValue
                        ORDER BY total_value DESC
                        """,
                params, this::map);
    }

    /** Product display name (or null). */
    public String productName(int companyId, long productId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT product_name FROM " + TenantSqlIdentifiers.inventoryProductTable(companyId)
                            + " WHERE product_id = :productId",
                    new MapSqlParameterSource("productId", productId), String.class);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /** Branch IDs where the product is out of stock or at/below its reorder level. */
    public List<Long> lowStockBranchIds(int companyId, long productId) {
        String stockTable = TenantSqlIdentifiers.inventoryBranchStockBalanceTable(companyId);
        String branchProductTable = TenantSqlIdentifiers.inventoryBranchProductTable(companyId);
        return jdbcTemplate.queryForList(
                "SELECT s.branch_id FROM " + stockTable + " s "
                        + "LEFT JOIN " + branchProductTable + " ibp ON ibp.product_id = s.product_id AND ibp.branch_id = s.branch_id "
                        + "WHERE s.product_id = :productId "
                        + "AND (s.quantity <= 0 OR (ibp.reorder_level IS NOT NULL AND s.quantity <= ibp.reorder_level)) "
                        + "ORDER BY s.branch_id",
                new MapSqlParameterSource("productId", productId), Long.class);
    }

    /** Branch IDs currently holding stock of the product (for dead-stock attribution). */
    public List<Long> stockedBranchIds(int companyId, long productId) {
        String stockTable = TenantSqlIdentifiers.inventoryBranchStockBalanceTable(companyId);
        return jdbcTemplate.queryForList(
                "SELECT branch_id FROM " + stockTable + " WHERE product_id = :productId AND quantity > 0 ORDER BY branch_id",
                new MapSqlParameterSource("productId", productId), Long.class);
    }

    private InventorySnapshotRow map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new InventorySnapshotRow(
                rs.getLong("product_id"),
                rs.getBigDecimal("total_qty"),
                rs.getBigDecimal("total_value"),
                rs.getInt("branch_count_with_stock"),
                rs.getInt("branches_below_reorder"),
                rs.getInt("branches_out_of_stock"),
                rs.getObject("last_sale_date", LocalDate.class),
                rs.getObject("last_movement_date", LocalDate.class),
                rs.getBoolean("is_dead_stock")
        );
    }

    public record InventorySnapshotRow(
            long productId,
            java.math.BigDecimal totalQty,
            java.math.BigDecimal totalValue,
            int branchCountWithStock,
            int branchesBelowReorder,
            int branchesOutOfStock,
            LocalDate lastSaleDate,
            LocalDate lastMovementDate,
            boolean deadStock
    ) {
    }
}
