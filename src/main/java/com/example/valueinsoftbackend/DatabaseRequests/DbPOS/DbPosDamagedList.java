/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.DatabaseRequests.DbPOS;

import com.example.valueinsoftbackend.Model.DamagedItem;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class DbPosDamagedList {

    private static final RowMapper<DamagedItem> DAMAGED_ITEM_ROW_MAPPER = new RowMapper<>() {
        @Override
        public DamagedItem mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new DamagedItem(
                    rs.getInt("DId"),
                    rs.getInt("ProductId"),
                    rs.getString("ProductName"),
                    rs.getTimestamp("Time"),
                    rs.getString("Reason"),
                    rs.getString("Damaged by"),
                    rs.getString("Cashier user"),
                    rs.getInt("AmountTP"),
                    rs.getBoolean("Paid"),
                    rs.getInt("branchId"),
                    rs.getInt("quantity")
            );
        }
    };

    private final JdbcTemplate jdbcTemplate;

    public DbPosDamagedList(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<DamagedItem> getDamagedList(int companyId, int branchId) {
        String sql = "SELECT \"DId\", \"ProductId\", \"ProductName\", \"Time\", \"Reason\", \"Damaged by\", " +
                "\"Cashier user\", \"AmountTP\", \"Paid\", \"branchId\", \"quantity\" " +
                "FROM " + TenantSqlIdentifiers.damagedListTable(companyId) + " WHERE \"branchId\" = ?";
        return jdbcTemplate.query(sql, DAMAGED_ITEM_ROW_MAPPER, branchId);
    }

    public Integer getProductQuantity(int companyId, int branchId, int productId) {
        String sql = "SELECT quantity FROM " + TenantSqlIdentifiers.productTable(companyId, branchId) +
                " WHERE \"productId\" = ?";
        List<Integer> quantities = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getInt("quantity"), productId);
        return quantities.isEmpty() ? null : quantities.get(0);
    }

    public int insertDamagedItem(int companyId, DamagedItem damagedItem) {
        String sql = "INSERT INTO " + TenantSqlIdentifiers.damagedListTable(companyId) + " " +
                "(\"ProductId\", \"ProductName\", \"Time\", \"Reason\", \"Damaged by\", \"Cashier user\", " +
                "\"AmountTP\", \"Paid\", \"branchId\", \"quantity\") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        return jdbcTemplate.update(
                sql,
                damagedItem.getProductId(),
                damagedItem.getProductName(),
                damagedItem.getTime(),
                damagedItem.getReason(),
                damagedItem.getDamagedBy(),
                damagedItem.getCashierUser(),
                damagedItem.getAmountTP(),
                damagedItem.isPaid(),
                damagedItem.getBranchId(),
                damagedItem.getQuantity()
        );
    }

    public int decrementProductQuantity(int companyId, int branchId, int productId, int quantity) {
        String sql = "UPDATE " + TenantSqlIdentifiers.productTable(companyId, branchId) + " " +
                "SET quantity = quantity - ? WHERE \"productId\" = ? AND quantity >= ?";
        return jdbcTemplate.update(sql, quantity, productId, quantity);
    }

    public int insertDamagedInventoryTransaction(int companyId, int branchId, DamagedItem damagedItem) {
        String sql = "INSERT INTO " + TenantSqlIdentifiers.inventoryTransactionsTable(companyId, branchId) + " " +
                "(\"productId\", \"userName\", \"supplierId\", \"transactionType\", \"NumItems\", \"transTotal\", " +
                "\"payType\", \"time\", \"RemainingAmount\") " +
                "VALUES (?, ?, COALESCE((SELECT \"supplierId\" FROM " + TenantSqlIdentifiers.productTable(companyId, branchId) +
                " WHERE \"productId\" = ?), 0), ?, ?, ?, ?, ?, ?)";
        return jdbcTemplate.update(
                sql,
                damagedItem.getProductId(),
                damagedItem.getCashierUser(),
                damagedItem.getProductId(),
                "Damaged",
                damagedItem.getQuantity() * -1,
                damagedItem.getAmountTP(),
                damagedItem.isPaid() ? "PaidDamaged" : "Damaged",
                damagedItem.getTime(),
                damagedItem.isPaid() ? 0 : damagedItem.getAmountTP()
        );
    }

    public boolean deleteDamagedItem(int companyId, int branchId, int damagedId) {
        String sql = "DELETE FROM " + TenantSqlIdentifiers.damagedListTable(companyId) +
                " WHERE \"DId\" = ? AND \"branchId\" = ?";
        return jdbcTemplate.update(sql, damagedId, branchId) == 1;
    }
}
