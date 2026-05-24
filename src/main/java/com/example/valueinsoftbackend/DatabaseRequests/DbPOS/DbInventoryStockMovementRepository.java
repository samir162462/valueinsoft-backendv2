package com.example.valueinsoftbackend.DatabaseRequests.DbPOS;

import com.example.valueinsoftbackend.Model.Inventory.InventoryMovementType;
import com.example.valueinsoftbackend.Model.Inventory.InventoryStockMovement;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class DbInventoryStockMovementRepository {

    private static final RowMapper<InventoryStockMovement> STOCK_MOVEMENT_ROW_MAPPER = (rs, rowNum) -> new InventoryStockMovement(
            rs.getLong("stock_movement_id"),
            rs.getLong("company_id"),
            getLongOrNull(rs, "branch_id"),
            rs.getLong("product_id"),
            getLongOrNull(rs, "product_unit_id"),
            InventoryMovementType.valueOf(rs.getString("movement_type")),
            rs.getBigDecimal("quantity_delta"),
            getLongOrNull(rs, "from_branch_id"),
            getLongOrNull(rs, "to_branch_id"),
            rs.getString("reference_type"),
            rs.getString("reference_id"),
            getLongOrNull(rs, "reference_line_id"),
            getLongOrNull(rs, "supplier_id"),
            getLongOrNull(rs, "customer_id"),
            getLongOrNull(rs, "actor_user_id"),
            rs.getString("actor_name"),
            rs.getString("note"),
            rs.getString("idempotency_key"),
            rs.getTimestamp("created_at")
    );

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public DbInventoryStockMovementRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long insertMovement(InventoryStockMovement movement) {
        String sql = """
                INSERT INTO %s (
                    company_id, branch_id, product_id, product_unit_id, movement_type,
                    quantity_delta, from_branch_id, to_branch_id, reference_type,
                    reference_id, reference_line_id, supplier_id, customer_id,
                    actor_user_id, actor_name, note, idempotency_key, created_at
                ) VALUES (
                    :companyId, :branchId, :productId, :productUnitId, :movementType,
                    :quantityDelta, :fromBranchId, :toBranchId, :referenceType,
                    :referenceId, :referenceLineId, :supplierId, :customerId,
                    :actorUserId, :actorName, :note, :idempotencyKey, CURRENT_TIMESTAMP
                )
                ON CONFLICT DO NOTHING
                """.formatted(TenantSqlIdentifiers.inventoryStockMovementTable(movement.getCompanyId()));

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, toParams(movement), keyHolder, new String[]{"stock_movement_id"});
        if (keyHolder.getKey() == null) {
            return 0;
        }
        return keyHolder.getKey().longValue();
    }

    public long insertHistoryLedgerMovement(InventoryStockMovement movement, Integer transTotal, String payType, Integer remainingAmount) {
        String sql = """
                INSERT INTO %s (
                    company_id, branch_id, product_id, product_unit_id, quantity_delta, movement_type,
                    reference_type, reference_id, actor_name, note, supplier_id, trans_total,
                    pay_type, remaining_amount, idempotency_key, created_at
                ) VALUES (
                    :companyId, :branchId, :productId, :productUnitId, :quantityDelta, :movementType,
                    :referenceType, :referenceId, :actorName, :note, :supplierId, :transTotal,
                    :payType, :remainingAmount, :idempotencyKey, CURRENT_TIMESTAMP
                )
                ON CONFLICT DO NOTHING
                RETURNING stock_ledger_id
                """.formatted(TenantSqlIdentifiers.inventoryStockLedgerTable(movement.getCompanyId()));

        List<Long> inserted = jdbcTemplate.query(
                sql,
                toLedgerParams(movement)
                        .addValue("transTotal", transTotal == null ? 0 : transTotal)
                        .addValue("payType", payType == null ? "" : payType)
                        .addValue("remainingAmount", remainingAmount == null ? 0 : remainingAmount),
                (rs, rowNum) -> rs.getLong("stock_ledger_id")
        );
        return inserted.isEmpty() ? 0 : inserted.getFirst();
    }

    public List<InventoryStockMovement> findByProduct(long companyId, long branchId, long productId, int limit) {
        String sql = """
                SELECT *
                FROM %s
                WHERE company_id = :companyId
                  AND branch_id = :branchId
                  AND product_id = :productId
                ORDER BY created_at DESC, stock_movement_id DESC
                LIMIT :limit
                """.formatted(TenantSqlIdentifiers.inventoryStockMovementTable(companyId));

        return jdbcTemplate.query(
                sql,
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("branchId", branchId)
                        .addValue("productId", productId)
                        .addValue("limit", Math.max(1, limit)),
                STOCK_MOVEMENT_ROW_MAPPER
        );
    }

    public List<InventoryStockMovement> findByProductUnit(long companyId, long productUnitId, int limit) {
        String sql = """
                SELECT *
                FROM %s
                WHERE company_id = :companyId
                  AND product_unit_id = :productUnitId
                ORDER BY created_at DESC, stock_movement_id DESC
                LIMIT :limit
                """.formatted(TenantSqlIdentifiers.inventoryStockMovementTable(companyId));

        return jdbcTemplate.query(
                sql,
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("productUnitId", productUnitId)
                        .addValue("limit", Math.max(1, limit)),
                STOCK_MOVEMENT_ROW_MAPPER
        );
    }

    private MapSqlParameterSource toParams(InventoryStockMovement movement) {
        return new MapSqlParameterSource()
                .addValue("companyId", movement.getCompanyId())
                .addValue("branchId", movement.getBranchId())
                .addValue("productId", movement.getProductId())
                .addValue("productUnitId", movement.getProductUnitId())
                .addValue("movementType", movement.getMovementType().name())
                .addValue("quantityDelta", movement.getQuantityDelta())
                .addValue("fromBranchId", movement.getFromBranchId())
                .addValue("toBranchId", movement.getToBranchId())
                .addValue("referenceType", movement.getReferenceType())
                .addValue("referenceId", movement.getReferenceId())
                .addValue("referenceLineId", movement.getReferenceLineId())
                .addValue("supplierId", movement.getSupplierId())
                .addValue("customerId", movement.getCustomerId())
                .addValue("actorUserId", movement.getActorUserId())
                .addValue("actorName", movement.getActorName())
                .addValue("note", movement.getNote())
                .addValue("idempotencyKey", movement.getIdempotencyKey());
    }

    private MapSqlParameterSource toLedgerParams(InventoryStockMovement movement) {
        return new MapSqlParameterSource()
                .addValue("companyId", movement.getCompanyId())
                .addValue("branchId", movement.getBranchId())
                .addValue("productId", movement.getProductId())
                .addValue("productUnitId", movement.getProductUnitId())
                .addValue("movementType", movement.getMovementType().name())
                .addValue("quantityDelta", movement.getQuantityDelta() == null ? 0 : movement.getQuantityDelta().intValue())
                .addValue("referenceType", movement.getReferenceType())
                .addValue("referenceId", movement.getReferenceId())
                .addValue("supplierId", movement.getSupplierId() == null ? 0 : movement.getSupplierId())
                .addValue("actorName", movement.getActorName())
                .addValue("note", movement.getNote())
                .addValue("idempotencyKey", movement.getIdempotencyKey());
    }

    private static Long getLongOrNull(ResultSet rs, String columnName) throws SQLException {
        long value = rs.getLong(columnName);
        return rs.wasNull() ? null : value;
    }
}
