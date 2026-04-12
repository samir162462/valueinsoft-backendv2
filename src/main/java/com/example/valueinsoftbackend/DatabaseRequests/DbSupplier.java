/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.DatabaseRequests;

import com.example.valueinsoftbackend.Model.InventoryTransaction;
import com.example.valueinsoftbackend.Model.Supplier;
import com.example.valueinsoftbackend.Model.SupplierBProduct;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import com.google.gson.JsonObject;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

@Repository
public class DbSupplier {

    private static final RowMapper<Supplier> SUPPLIER_ROW_MAPPER = (rs, rowNum) -> new Supplier(
            rs.getInt("supplierId"),
            rs.getString("SupplierName"),
            rs.getString("supplierPhone1"),
            rs.getString("supplierPhone2"),
            rs.getString("SupplierLocation"),
            rs.getString("suplierMajor"),
            rs.getInt("supplierTotalSales"),
            rs.getInt("supplierRemainig")
    );

    private static final RowMapper<InventoryTransaction> INVENTORY_TRANSACTION_ROW_MAPPER = (rs, rowNum) -> new InventoryTransaction(
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
            rs.getInt("runningBalance")
    );

    private static final RowMapper<SupplierBProduct> SUPPLIER_B_PRODUCT_ROW_MAPPER = (rs, rowNum) -> new SupplierBProduct(
            rs.getInt("sBPId"),
            rs.getInt("productId"),
            rs.getInt("quantity"),
            rs.getInt("cost"),
            rs.getString("userName"),
            rs.getInt("sPaid"),
            rs.getTimestamp("time"),
            rs.getString("desc"),
            rs.getInt("orderDetailsId")
    );

    private final JdbcTemplate jdbcTemplate;

    public DbSupplier(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Supplier> getSuppliers(int branchId, int companyId) {
        String sql = "SELECT \"supplierId\", \"SupplierName\", \"supplierPhone1\", \"supplierPhone2\", " +
                "\"SupplierLocation\", \"suplierMajor\", \"supplierTotalSales\", \"supplierRemainig\" " +
                "FROM " + TenantSqlIdentifiers.supplierTable(companyId, branchId);
        return jdbcTemplate.query(sql, SUPPLIER_ROW_MAPPER);
    }

    public int addSupplier(String name, String phone1, String phone2, String location, String major, int branchId, int companyId) {
        String sql = "INSERT INTO " + TenantSqlIdentifiers.supplierTable(companyId, branchId) +
                " (\"SupplierName\", \"supplierPhone1\", \"supplierPhone2\", \"SupplierLocation\", \"suplierMajor\") " +
                "VALUES (?, ?, ?, ?, ?)";
        return jdbcTemplate.update(sql, name, phone1, phone2, location, major);
    }

    public int updateSupplier(int supplierId, String name, String phone1, String phone2, String location, String major, int branchId, int companyId) {
        String sql = "UPDATE " + TenantSqlIdentifiers.supplierTable(companyId, branchId) +
                " SET \"SupplierName\" = ?, \"supplierPhone1\" = ?, \"supplierPhone2\" = ?, \"SupplierLocation\" = ?, \"suplierMajor\" = ? " +
                "WHERE \"supplierId\" = ?";
        return jdbcTemplate.update(sql, name, phone1, phone2, location, major, supplierId);
    }

    public int deleteSupplier(int supplierId, int branchId, int companyId) {
        String sql = "DELETE FROM " + TenantSqlIdentifiers.supplierTable(companyId, branchId) +
                " WHERE \"supplierId\" = ?";
        return jdbcTemplate.update(sql, supplierId);
    }

    public JsonObject getRemainingSupplierAmountByProductId(int productId, int branchId, int companyId) {
        String sql = "SELECT ledger.product_id AS \"productId\", ledger.created_at AS \"time\", " +
                "COALESCE(ledger.pay_type, '') AS \"payType\", COALESCE(ledger.remaining_amount, 0) AS \"remainingAmount\" " +
                "FROM " + TenantSqlIdentifiers.inventoryStockLedgerTable(companyId) + " ledger " +
                "WHERE ledger.branch_id = ? AND ledger.product_id = ? " +
                "ORDER BY ledger.created_at DESC, ledger.stock_ledger_id DESC LIMIT 1";

        ResultSetExtractor<JsonObject> extractor = rs -> {
            JsonObject json = new JsonObject();
            if (rs.next()) {
                json.addProperty("productId", rs.getInt("productId"));
                json.addProperty("time", rs.getString("time"));
                json.addProperty("payType", rs.getString("payType"));
                json.addProperty("remainingAmount", rs.getInt("remainingAmount"));
            }
            return json;
        };

        return jdbcTemplate.query(sql, extractor, branchId, productId);
    }

    public List<InventoryTransaction> getSupplierSales(int branchId, int supplierId, int companyId) {
        String sql = """
                SELECT
                    ledger.stock_ledger_id AS "transId",
                    ledger.product_id AS "productId",
                    prod.product_name AS "productName",
                    prod.serial AS "serial",
                    COALESCE(ledger.actor_name, 'system') AS "userName",
                    COALESCE(ledger.supplier_id, 0) AS "supplierId",
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
                    SUM(ledger.quantity_delta) OVER (
                        PARTITION BY ledger.branch_id, ledger.product_id
                        ORDER BY ledger.created_at ASC, ledger.stock_ledger_id ASC
                    ) AS "runningBalance"
                FROM %s ledger
                JOIN %s prod ON prod.product_id = ledger.product_id
                WHERE ledger.branch_id = ? AND COALESCE(ledger.supplier_id, 0) = ?
                ORDER BY ledger.created_at DESC, ledger.stock_ledger_id DESC
                """.formatted(
                TenantSqlIdentifiers.inventoryStockLedgerTable(companyId),
                TenantSqlIdentifiers.inventoryProductTable(companyId)
        );
        return jdbcTemplate.query(sql, INVENTORY_TRANSACTION_ROW_MAPPER, branchId, supplierId);
    }

    public List<SupplierBProduct> getSupplierBProduct(int branchId, int supplierId, int companyId) {
        String sql = "SELECT \"sBPId\", \"productId\", quantity, cost, \"userName\", \"sPaid\", " +
                "\"time\", \"desc\", \"orderDetailsId\" FROM " + TenantSqlIdentifiers.supplierBoughtProductTable(companyId) +
                " WHERE \"branchId\" = ? AND \"supplierId\" = ?";
        return jdbcTemplate.query(sql, SUPPLIER_B_PRODUCT_ROW_MAPPER, branchId, supplierId);
    }

    public int addSupplierBProduct(SupplierBProduct supplierBProduct, int productId, int branchId, int companyId) {
        String sql = "INSERT INTO " + TenantSqlIdentifiers.supplierBoughtProductTable(companyId) +
                " (\"productId\", quantity, cost, \"userName\", \"sPaid\", \"time\", \"desc\", \"orderDetailsId\", \"supplierId\", \"branchId\") " +
                "SELECT ?, ?, ?, ?, ?, ?, ?, ?, p.supplier_id, ? " +
                "FROM " + TenantSqlIdentifiers.inventoryProductTable(companyId) + " p " +
                "WHERE p.product_id = ?";

        return jdbcTemplate.update(
                sql,
                productId,
                supplierBProduct.getQuantity(),
                supplierBProduct.getCost(),
                supplierBProduct.getUserName(),
                supplierBProduct.getsPaid(),
                new Timestamp(supplierBProduct.getTime().getTime()),
                supplierBProduct.getDesc(),
                supplierBProduct.getOrderDetailsId(),
                branchId,
                productId
        );
    }
}
