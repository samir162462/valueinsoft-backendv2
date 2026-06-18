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
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
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
                WITH ledger_source AS (
                    SELECT
                        ledger.stock_ledger_id,
                        ledger.branch_id,
                        ledger.product_id,
                        ledger.product_unit_id,
                        ledger.movement_type,
                        ledger.quantity_delta,
                        ledger.reference_type,
                        ledger.reference_id,
                        ledger.actor_name,
                        ledger.supplier_id,
                        ledger.trans_total,
                        ledger.pay_type,
                        ledger.remaining_amount,
                        ledger.created_at
                    FROM %s ledger

                    UNION ALL

                    SELECT
                        -unit.product_unit_id AS stock_ledger_id,
                        unit.branch_id,
                        unit.product_id,
                        unit.product_unit_id,
                        'STOCK_IN' AS movement_type,
                        1 AS quantity_delta,
                        COALESCE(NULLIF(unit.purchase_reference_type, ''), 'SERIALIZED_UNIT') AS reference_type,
                        COALESCE(NULLIF(unit.purchase_reference_id, ''), unit.product_unit_id::text) AS reference_id,
                        NULL::varchar AS actor_name,
                        COALESCE(unit.supplier_id, 0)::integer AS supplier_id,
                        0 AS trans_total,
                        '' AS pay_type,
                        0 AS remaining_amount,
                        COALESCE(unit.received_at, unit.created_at) AS created_at
                    FROM %s unit
                    JOIN %s unit_product ON unit_product.product_id = unit.product_id
                    WHERE unit.branch_id = ?
                      AND COALESCE(unit_product.tracking_type, 'QUANTITY') IN ('IMEI', 'SERIAL')
                      AND NOT EXISTS (
                          SELECT 1
                          FROM %s existing_ledger
                          WHERE existing_ledger.product_unit_id = unit.product_unit_id
                            AND existing_ledger.quantity_delta > 0
                            AND existing_ledger.movement_type IN ('STOCK_IN', 'OPENING_BALANCE', 'MANUAL_STOCK_IN')
                      )
                ),
                grouped_ledger AS (
                    SELECT
                        MIN(ledger.stock_ledger_id) AS stock_ledger_id,
                        ledger.product_id,
                        prod.product_name,
                        COALESCE(
                            string_agg(DISTINCT COALESCE(NULLIF(unit.imei, ''), NULLIF(unit.serial_number, '')), ', ')
                                FILTER (WHERE COALESCE(NULLIF(unit.imei, ''), NULLIF(unit.serial_number, '')) IS NOT NULL),
                            NULLIF(prod.serial, ''),
                            NULLIF(prod.barcode, ''),
                            ''
                        ) AS serial,
                        COALESCE(MAX(ledger.actor_name), MAX(tx."userName"), 'system') AS actor_name,
                        COALESCE(ledger.supplier_id, unit.supplier_id, tx."supplierId", prod.supplier_id, 0) AS supplier_id,
                        ledger.movement_type,
                        SUM(ledger.quantity_delta)::integer AS quantity_delta,
                        CASE
                            WHEN COALESCE(prod.tracking_type, 'QUANTITY') IN ('IMEI', 'SERIAL')
                                AND ledger.product_unit_id IS NOT NULL
                                AND SUM(ledger.quantity_delta) <> 0
                            THEN COALESCE(
                                MAX(tx."transTotal") / NULLIF(ABS(MAX(tx."NumItems")), 0),
                                SUM(COALESCE(ledger.trans_total, 0))
                            )
                            ELSE COALESCE(MAX(tx."transTotal"), SUM(COALESCE(ledger.trans_total, 0)))
                        END::integer AS trans_total,
                        COALESCE(MAX(tx."payType"), MAX(ledger.pay_type), '') AS pay_type,
                        MIN(ledger.created_at) AS created_at,
                        COALESCE(MAX(tx."RemainingAmount"), SUM(COALESCE(ledger.remaining_amount, 0)))::integer AS remaining_amount,
                        COALESCE(prod.business_line_key, '') AS business_line_key,
                        COALESCE(prod.template_key, '') AS template_key
                    FROM ledger_source ledger
                    JOIN %s prod ON prod.product_id = ledger.product_id
                    LEFT JOIN %s unit ON unit.product_unit_id = ledger.product_unit_id
                    LEFT JOIN %s tx ON ledger.reference_type = 'INVENTORY_TRANSACTION'
                        AND ledger.reference_id = tx."transId"::text
                    WHERE ledger.branch_id = ?
                      AND NOT (
                          COALESCE(prod.tracking_type, 'QUANTITY') IN ('IMEI', 'SERIAL')
                          AND ledger.reference_type = 'PRODUCT_CREATE'
                          AND ledger.movement_type = 'OPENING_BALANCE'
                      )
                    GROUP BY
                        CASE
                            WHEN COALESCE(prod.tracking_type, 'QUANTITY') IN ('IMEI', 'SERIAL')
                                AND ledger.movement_type IN ('SALE', 'RETURN')
                                AND ledger.product_unit_id IS NOT NULL
                            THEN concat_ws('|',
                                ledger.product_id::text,
                                ledger.movement_type,
                                COALESCE(ledger.reference_type, ''),
                                COALESCE(ledger.reference_id, ''),
                                ledger.product_unit_id::text
                            )
                            WHEN COALESCE(prod.tracking_type, 'QUANTITY') IN ('IMEI', 'SERIAL')
                                AND ledger.product_unit_id IS NOT NULL
                            THEN concat_ws('|',
                                ledger.product_id::text,
                                ledger.movement_type,
                                COALESCE(ledger.reference_type, ''),
                                COALESCE(ledger.reference_id, ''),
                                ledger.product_unit_id::text
                            )
                            ELSE ledger.stock_ledger_id::text
                        END,
                        ledger.product_id,
                        prod.product_name,
                        prod.serial,
                        prod.barcode,
                        prod.tracking_type,
                        prod.business_line_key,
                        prod.template_key,
                        prod.supplier_id,
                        ledger.product_unit_id,
                        ledger.movement_type,
                        COALESCE(ledger.supplier_id, unit.supplier_id, tx."supplierId", prod.supplier_id, 0)
                ),
                ledger_with_balance AS (
                    SELECT
                        *,
                        SUM(quantity_delta) OVER (
                            PARTITION BY product_id
                            ORDER BY created_at ASC, stock_ledger_id ASC
                        ) AS running_balance
                    FROM grouped_ledger
                )
                SELECT
                    stock_ledger_id AS "transId",
                    product_id AS "productId",
                    product_name AS "productName",
                    serial AS "serial",
                    actor_name AS "userName",
                    supplier_id AS "supplierId",
                    CASE movement_type
                        WHEN 'SALE_OUT' THEN 'Sold'
                        WHEN 'SALE' THEN 'Sold'
                        WHEN 'BOUNCE_BACK_IN' THEN 'BounceBackInv'
                        WHEN 'RETURN' THEN 'BounceBackInv'
                        WHEN 'OPENING_BALANCE' THEN 'Add'
                        WHEN 'MANUAL_STOCK_IN' THEN 'Add'
                        WHEN 'STOCK_IN' THEN 'Add'
                        WHEN 'MANUAL_STOCK_OUT' THEN 'Update'
                        WHEN 'DAMAGED_OUT' THEN 'Damaged'
                        WHEN 'DAMAGE' THEN 'Damaged'
                        ELSE movement_type
                    END AS "transactionType",
                    quantity_delta AS "NumItems",
                    trans_total AS "transTotal",
                    pay_type AS "payType",
                    created_at AS "time",
                    remaining_amount AS "RemainingAmount",
                    business_line_key AS "businessLineKey",
                    template_key AS "templateKey",
                    running_balance AS "runningBalance"
                FROM ledger_with_balance
                WHERE created_at >= date_trunc('month', ?::timestamp)
                  AND created_at < date_trunc('month', ?::timestamp) + interval '1 month'
                ORDER BY created_at DESC, stock_ledger_id DESC
                """.formatted(
                TenantSqlIdentifiers.inventoryStockLedgerTable(companyId),
                TenantSqlIdentifiers.inventoryProductUnitTable(companyId),
                TenantSqlIdentifiers.inventoryProductTable(companyId),
                TenantSqlIdentifiers.inventoryStockLedgerTable(companyId),
                TenantSqlIdentifiers.inventoryProductTable(companyId),
                TenantSqlIdentifiers.inventoryProductUnitTable(companyId),
                TenantSqlIdentifiers.inventoryTransactionsTable(companyId, branchId)
        );
        return jdbcTemplate.query(sql, new InventoryTransactionMapper(), branchId, branchId, startDate, endDate);
    }

    public AddInventoryTransactionResult insertInventoryTransaction(InventoryTransaction inventoryTransaction, int branchId, int companyId) {
        String sql = "INSERT INTO " + TenantSqlIdentifiers.inventoryTransactionsTable(companyId, branchId) +
                " (\"productId\", \"userName\", \"supplierId\", \"transactionType\", \"NumItems\", \"transTotal\", \"payType\", \"time\", \"RemainingAmount\") " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"transId"});
            ps.setInt(1, inventoryTransaction.getProductId());
            ps.setString(2, inventoryTransaction.getUserName());
            ps.setInt(3, inventoryTransaction.getSupplierId());
            ps.setString(4, inventoryTransaction.getTransactionType());
            ps.setInt(5, inventoryTransaction.getNumItems());
            ps.setInt(6, inventoryTransaction.getTransTotal());
            ps.setString(7, inventoryTransaction.getPayType());
            ps.setTimestamp(8, new Timestamp(inventoryTransaction.getTime().getTime()));
            ps.setInt(9, inventoryTransaction.getRemainingAmount());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        Integer transactionId = key == null ? null : key.intValue();
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
                UPDATE %s ledger
                SET supplier_id = :supplierId,
                    trans_total = :transTotal,
                    pay_type = :payType,
                    remaining_amount = :remainingAmount,
                    actor_name = COALESCE(NULLIF(ledger.actor_name, ''), :userName)
                WHERE ledger.stock_ledger_id = (
                    SELECT stock_ledger_id
                    FROM %s
                    WHERE branch_id = :branchId
                      AND product_id = :productId
                      AND movement_type IN ('OPENING_BALANCE', 'MANUAL_STOCK_IN', 'MANUAL_STOCK_OUT')
                    ORDER BY created_at DESC, stock_ledger_id DESC
                    LIMIT 1
                )
                """.formatted(TenantSqlIdentifiers.inventoryStockLedgerTable(companyId),
                TenantSqlIdentifiers.inventoryStockLedgerTable(companyId));

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
