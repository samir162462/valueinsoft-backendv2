/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.DatabaseRequests.DbPOS;

import com.example.valueinsoftbackend.Model.InventoryTransaction;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

@Repository
public class DbPosInventoryTransaction {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public DbPosInventoryTransaction(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    public static class InventoryTransactionMapper implements RowMapper<InventoryTransaction> {
        @Override
        public InventoryTransaction mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new InventoryTransaction(
                    rs.getInt("transId"),
                    rs.getInt("productId"),
                    rs.getString("productName"),
                    rs.getString("serial"),
                    rs.getString("userName"),
                    rs.getInt("supplierId"),
                    rs.getString("transactionType"),
                    rs.getInt("NumItems"),
                    rs.getInt("transTotal"),
                    rs.getString("payType"),
                    rs.getTimestamp("time"),
                    rs.getInt("RemainingAmount"),
                    rs.getInt("runningBalance"),
                    rs.getString("businessLineKey"),
                    rs.getString("templateKey")
            );
        }
    }

    public List<InventoryTransaction> getInventoryTrans(int companyId, int branchId, String startDate, String endDate) {
        String sql = """
                SELECT
                    ledger.stock_ledger_id AS "transId",
                    ledger.product_id AS "productId",
                    prod.product_name AS "productName",
                    prod.serial AS "serial",
                    COALESCE(ledger.actor_name, 'system') AS "userName",
                    COALESCE(ledger.supplier_id, prod.supplier_id, 0) AS "supplierId",
                    CASE ledger.movement_type
                        WHEN 'SALE_OUT' THEN 'Sold'
                        WHEN 'BOUNCE_BACK_IN' THEN 'BounceBackInv'
                        WHEN 'OPENING_BALANCE' THEN 'Add'
                        WHEN 'MANUAL_STOCK_IN' THEN 'Add'
                        WHEN 'MANUAL_STOCK_OUT' THEN 'Update'
                        WHEN 'DAMAGED_OUT' THEN 'Damaged'
                        ELSE ledger.movement_type
                    END AS "transactionType",
                    ledger.quantity_delta AS "NumItems",
                    COALESCE(ledger.trans_total, 0) AS "transTotal",
                    COALESCE(ledger.pay_type, '') AS "payType",
                    ledger.created_at AS "time",
                    COALESCE(ledger.remaining_amount, 0) AS "RemainingAmount",
                    COALESCE(prod.business_line_key, '') AS "businessLineKey",
                    COALESCE(prod.template_key, '') AS "templateKey",
                    SUM(ledger.quantity_delta) OVER (
                        PARTITION BY ledger.branch_id, ledger.product_id
                        ORDER BY ledger.created_at ASC, ledger.stock_ledger_id ASC
                    ) AS "runningBalance"
                FROM %s ledger
                JOIN %s prod ON prod.product_id = ledger.product_id
                WHERE ledger.branch_id = ?
                  AND ledger.created_at >= date_trunc('month', ?::timestamp)
                  AND ledger.created_at < date_trunc('month', ?::timestamp) + interval '1 month'
                ORDER BY ledger.created_at DESC, ledger.stock_ledger_id DESC
                """.formatted(
                TenantSqlIdentifiers.inventoryStockLedgerTable(companyId),
                TenantSqlIdentifiers.inventoryProductTable(companyId)
        );
        return jdbcTemplate.query(sql, new InventoryTransactionMapper(), branchId, startDate, endDate);
    }

    public AddInventoryTransactionResult insertInventoryTransaction(InventoryTransaction inventoryTransaction, int branchId, int companyId) {
        String sql = "INSERT INTO " + TenantSqlIdentifiers.inventoryTransactionsTable(companyId, branchId) +
                " (\"productId\", \"userName\", \"supplierId\", \"transactionType\", \"NumItems\", \"transTotal\", \"payType\", \"time\", \"RemainingAmount\") " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING \"transId\"";
        Integer transactionId = jdbcTemplate.queryForObject(
                sql,
                Integer.class,
                inventoryTransaction.getProductId(),
                inventoryTransaction.getUserName(),
                inventoryTransaction.getSupplierId(),
                inventoryTransaction.getTransactionType(),
                inventoryTransaction.getNumItems(),
                inventoryTransaction.getTransTotal(),
                inventoryTransaction.getPayType(),
                new Timestamp(inventoryTransaction.getTime().getTime()),
                inventoryTransaction.getRemainingAmount()
        );
        return new AddInventoryTransactionResult(transactionId == null ? 0 : transactionId);
    }

    public Long findLatestPurchaseInventoryMovementId(int companyId, int branchId, InventoryTransaction inventoryTransaction) {
        String sql = """
                SELECT stock_ledger_id
                FROM %s
                WHERE branch_id = :branchId
                  AND product_id = :productId
                  AND quantity_delta > 0
                  AND movement_type IN ('OPENING_BALANCE', 'MANUAL_STOCK_IN')
                  AND COALESCE(supplier_id, 0) = :supplierId
                  AND COALESCE(trans_total, 0) = :transTotal
                ORDER BY created_at DESC, stock_ledger_id DESC
                LIMIT 1
                """.formatted(TenantSqlIdentifiers.inventoryStockLedgerTable(companyId));

        List<Long> movementIds = namedParameterJdbcTemplate.query(
                sql,
                new MapSqlParameterSource()
                        .addValue("branchId", branchId)
                        .addValue("productId", inventoryTransaction.getProductId())
                        .addValue("supplierId", inventoryTransaction.getSupplierId())
                        .addValue("transTotal", inventoryTransaction.getTransTotal()),
                (rs, rowNum) -> rs.getLong("stock_ledger_id")
        );
        return movementIds.isEmpty() ? null : movementIds.getFirst();
    }

    public Long findLatestAdjustmentInventoryMovementId(int companyId, int branchId, InventoryTransaction inventoryTransaction) {
        String sql = """
                SELECT stock_ledger_id
                FROM %s
                WHERE branch_id = :branchId
                  AND product_id = :productId
                  AND quantity_delta = :quantityDelta
                  AND movement_type IN ('MANUAL_STOCK_IN', 'MANUAL_STOCK_OUT')
                  AND COALESCE(trans_total, 0) = :transTotal
                ORDER BY created_at DESC, stock_ledger_id DESC
                LIMIT 1
                """.formatted(TenantSqlIdentifiers.inventoryStockLedgerTable(companyId));

        List<Long> movementIds = namedParameterJdbcTemplate.query(
                sql,
                new MapSqlParameterSource()
                        .addValue("branchId", branchId)
                        .addValue("productId", inventoryTransaction.getProductId())
                        .addValue("quantityDelta", inventoryTransaction.getNumItems())
                        .addValue("transTotal", inventoryTransaction.getTransTotal()),
                (rs, rowNum) -> rs.getLong("stock_ledger_id")
        );
        return movementIds.isEmpty() ? null : movementIds.getFirst();
    }

    public record AddInventoryTransactionResult(int transactionId) {}

    public int updateSupplierTotals(int companyId, int branchId, int supplierId, int transTotal, int remainingAmount) {
        String sql = "UPDATE " + TenantSqlIdentifiers.supplierTable(companyId, branchId) +
                " SET \"supplierRemainig\" = \"supplierRemainig\" + ?, " +
                "\"supplierTotalSales\" = \"supplierTotalSales\" + ? WHERE \"supplierId\" = ?";
        return jdbcTemplate.update(sql, transTotal, remainingAmount, supplierId);
    }

    public int syncLatestLedgerMetadata(int companyId, int branchId, InventoryTransaction inventoryTransaction) {
        String sql = """
                WITH latest_ledger AS (
                    SELECT stock_ledger_id
                    FROM %s
                    WHERE branch_id = :branchId
                      AND product_id = :productId
                      AND movement_type IN ('OPENING_BALANCE', 'MANUAL_STOCK_IN', 'MANUAL_STOCK_OUT')
                    ORDER BY created_at DESC, stock_ledger_id DESC
                    LIMIT 1
                )
                UPDATE %s ledger
                SET supplier_id = :supplierId,
                    trans_total = :transTotal,
                    pay_type = :payType,
                    remaining_amount = :remainingAmount,
                    actor_name = COALESCE(NULLIF(ledger.actor_name, ''), :userName)
                FROM latest_ledger
                WHERE ledger.stock_ledger_id = latest_ledger.stock_ledger_id
                """.formatted(
                TenantSqlIdentifiers.inventoryStockLedgerTable(companyId),
                TenantSqlIdentifiers.inventoryStockLedgerTable(companyId)
        );

        return namedParameterJdbcTemplate.update(
                sql,
                new MapSqlParameterSource()
                        .addValue("branchId", branchId)
                        .addValue("productId", inventoryTransaction.getProductId())
                        .addValue("supplierId", inventoryTransaction.getSupplierId())
                        .addValue("transTotal", inventoryTransaction.getTransTotal())
                        .addValue("payType", inventoryTransaction.getPayType())
                        .addValue("remainingAmount", inventoryTransaction.getRemainingAmount())
                        .addValue("userName", inventoryTransaction.getUserName())
        );
    }
}
