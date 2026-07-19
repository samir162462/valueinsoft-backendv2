package com.example.valueinsoftbackend.DatabaseRequests.DbPOS;

import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class DbInventoryDamageRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public DbInventoryDamageRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long insertDamage(int companyId,
                             int branchId,
                             long productId,
                             String productName,
                             int quantity,
                             int unitCost,
                             int inventoryValue,
                             String reason,
                             String damagedBy,
                             String actorName,
                             int liabilityAmount,
                             String operationId,
                             long balanceVersionAfter) {
        String sql = """
                INSERT INTO %s (
                    "ProductId", "ProductName", "Time", "Reason", "Damaged by", "Cashier user",
                    "AmountTP", "Paid", "branchId", quantity, "OperationId", "Status",
                    "UnitCost", "InventoryValue", "BalanceVersionAfter"
                ) VALUES (
                    :productId, :productName, CURRENT_TIMESTAMP, :reason, :damagedBy, :actorName,
                    :liabilityAmount, FALSE, :branchId, :quantity, :operationId, 'POSTED',
                    :unitCost, :inventoryValue, :balanceVersionAfter
                )
                RETURNING "DId"
                """.formatted(TenantSqlIdentifiers.damagedListTable(companyId));
        List<Long> ids = jdbc.query(sql, new MapSqlParameterSource()
                .addValue("productId", productId)
                .addValue("productName", productName)
                .addValue("reason", reason)
                .addValue("damagedBy", damagedBy)
                .addValue("actorName", actorName)
                .addValue("liabilityAmount", liabilityAmount)
                .addValue("branchId", branchId)
                .addValue("quantity", quantity)
                .addValue("operationId", operationId)
                .addValue("unitCost", unitCost)
                .addValue("inventoryValue", inventoryValue)
                .addValue("balanceVersionAfter", balanceVersionAfter),
                (rs, rowNum) -> rs.getLong("DId"));
        return ids.isEmpty() ? 0 : ids.getFirst();
    }

    public Optional<DamageSnapshot> findForUpdate(int companyId, int branchId, long damageId) {
        String sql = """
                SELECT "DId", "ProductId", "ProductName", quantity, "Reason", "Damaged by",
                       "Cashier user", COALESCE("AmountTP", 0) AS liability_amount,
                       COALESCE("Paid", FALSE) AS paid, COALESCE("Status", 'POSTED') AS status,
                       COALESCE("UnitCost", 0) AS unit_cost,
                       COALESCE("InventoryValue", 0) AS inventory_value
                FROM %s
                WHERE "branchId" = :branchId AND "DId" = :damageId
                FOR UPDATE
                """.formatted(TenantSqlIdentifiers.damagedListTable(companyId));
        List<DamageSnapshot> rows = jdbc.query(sql, new MapSqlParameterSource()
                .addValue("branchId", branchId)
                .addValue("damageId", damageId), (rs, rowNum) -> new DamageSnapshot(
                rs.getLong("DId"), rs.getLong("ProductId"), rs.getString("ProductName"),
                rs.getInt("quantity"), rs.getString("Reason"), rs.getString("Damaged by"),
                rs.getString("Cashier user"), rs.getInt("liability_amount"), rs.getBoolean("paid"),
                rs.getString("status"), rs.getInt("unit_cost"), rs.getInt("inventory_value")));
        return rows.stream().findFirst();
    }

    public boolean markReversed(int companyId,
                                int branchId,
                                long damageId,
                                String actorName,
                                String reversalReason,
                                String reversalOperationId,
                                long balanceVersionAfter) {
        String sql = """
                UPDATE %s
                SET "Status" = 'REVERSED', "ReversedAt" = CURRENT_TIMESTAMP,
                    "ReversedBy" = :actorName, "ReversalReason" = :reversalReason,
                    "ReversalOperationId" = :reversalOperationId,
                    "BalanceVersionAfter" = :balanceVersionAfter
                WHERE "branchId" = :branchId AND "DId" = :damageId AND "Status" = 'POSTED'
                """.formatted(TenantSqlIdentifiers.damagedListTable(companyId));
        return jdbc.update(sql, new MapSqlParameterSource()
                .addValue("actorName", actorName)
                .addValue("reversalReason", reversalReason)
                .addValue("reversalOperationId", reversalOperationId)
                .addValue("balanceVersionAfter", balanceVersionAfter)
                .addValue("branchId", branchId)
                .addValue("damageId", damageId)) == 1;
    }

    public record DamageSnapshot(long damageId, long productId, String productName, int quantity,
                                 String reason, String damagedBy, String actorName, int liabilityAmount,
                                 boolean paid, String status, int unitCost, int inventoryValue) {
    }
}
