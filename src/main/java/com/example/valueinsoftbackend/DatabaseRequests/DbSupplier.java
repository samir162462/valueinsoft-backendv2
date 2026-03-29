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
            rs.getString("userName"),
            rs.getInt("supplierId"),
            rs.getString("transactionType"),
            rs.getInt("NumItems"),
            rs.getInt("transTotal"),
            rs.getString("payType"),
            rs.getTimestamp("time"),
            rs.getInt("RemainingAmount")
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
        String sql = "SELECT p.\"productId\", t.\"time\", t.\"payType\" AS \"payType\", t.\"RemainingAmount\" AS \"remainingAmount\" " +
                "FROM " + TenantSqlIdentifiers.productTable(companyId, branchId) + " p " +
                "INNER JOIN " + TenantSqlIdentifiers.inventoryTransactionsTable(companyId, branchId) + " t " +
                "ON p.\"productId\" = t.\"productId\" WHERE p.\"productId\" = ? " +
                "ORDER BY t.\"time\" DESC LIMIT 1";

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

        return jdbcTemplate.query(sql, extractor, productId);
    }

    public List<InventoryTransaction> getSupplierSales(int branchId, int supplierId, int companyId) {
        String sql = "SELECT \"transId\", \"productId\", \"userName\", \"supplierId\", \"transactionType\", " +
                "\"NumItems\", \"transTotal\", \"payType\", \"time\", \"RemainingAmount\" " +
                "FROM " + TenantSqlIdentifiers.inventoryTransactionsTable(companyId, branchId) +
                " WHERE \"supplierId\" = ?";
        return jdbcTemplate.query(sql, INVENTORY_TRANSACTION_ROW_MAPPER, supplierId);
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
                "SELECT ?, ?, ?, ?, ?, ?, ?, ?, p.\"supplierId\", ? " +
                "FROM " + TenantSqlIdentifiers.productTable(companyId, branchId) + " p " +
                "WHERE p.\"productId\" = ?";

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
