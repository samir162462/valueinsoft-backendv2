/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.DatabaseRequests.DbPOS;

import com.example.valueinsoftbackend.Model.InventoryTransaction;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

@Repository
public class DbPosInventoryTransaction {

    private final JdbcTemplate jdbcTemplate;

    public DbPosInventoryTransaction(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public static class InventoryTransactionMapper implements RowMapper<InventoryTransaction> {
        @Override
        public InventoryTransaction mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new InventoryTransaction(
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
        }
    }

    public List<InventoryTransaction> getInventoryTrans(int companyId, int branchId, String startDate, String endDate) {
        String sql = "SELECT \"transId\", \"productId\", \"userName\", \"supplierId\", \"transactionType\", " +
                "\"NumItems\", \"transTotal\", \"payType\", \"time\", \"RemainingAmount\" " +
                "FROM " + TenantSqlIdentifiers.inventoryTransactionsTable(companyId, branchId) +
                " WHERE \"time\" >= date_trunc('month', ?::timestamp) " +
                "AND \"time\" < date_trunc('month', ?::timestamp) + interval '1 month'";
        return jdbcTemplate.query(sql, new InventoryTransactionMapper(), startDate, endDate);
    }

    public int insertInventoryTransaction(InventoryTransaction inventoryTransaction, int branchId, int companyId) {
        String sql = "INSERT INTO " + TenantSqlIdentifiers.inventoryTransactionsTable(companyId, branchId) +
                " (\"productId\", \"userName\", \"supplierId\", \"transactionType\", \"NumItems\", \"transTotal\", \"payType\", \"time\", \"RemainingAmount\") " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        return jdbcTemplate.update(
                sql,
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
    }

    public int updateSupplierTotals(int companyId, int branchId, int supplierId, int transTotal, int remainingAmount) {
        String sql = "UPDATE " + TenantSqlIdentifiers.supplierTable(companyId, branchId) +
                " SET \"supplierRemainig\" = \"supplierRemainig\" + ?, " +
                "\"supplierTotalSales\" = \"supplierTotalSales\" + ? WHERE \"supplierId\" = ?";
        return jdbcTemplate.update(sql, transTotal, remainingAmount, supplierId);
    }
}
