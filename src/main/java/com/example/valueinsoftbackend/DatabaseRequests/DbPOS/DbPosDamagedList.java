/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.DatabaseRequests.DbPOS;

import com.example.valueinsoftbackend.Model.DamagedItem;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
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
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public DbPosDamagedList(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    public List<DamagedItem> getDamagedList(int companyId, int branchId) {
        String sql = "SELECT \"DId\", \"ProductId\", \"ProductName\", \"Time\", \"Reason\", \"Damaged by\", " +
                "\"Cashier user\", \"AmountTP\", \"Paid\", \"branchId\", \"quantity\" " +
                "FROM " + TenantSqlIdentifiers.damagedListTable(companyId) + " WHERE \"branchId\" = ?";
        return jdbcTemplate.query(sql, DAMAGED_ITEM_ROW_MAPPER, branchId);
    }

    public Integer getProductQuantity(int companyId, int branchId, int productId) {
        String sql = "SELECT quantity FROM " + TenantSqlIdentifiers.inventoryBranchStockBalanceTable(companyId) + " " +
                "WHERE branch_id = ? AND product_id = ?";
        List<Integer> quantities = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getInt("quantity"), branchId, productId);
        return quantities.isEmpty() ? null : quantities.get(0);
    }

    public AddDamagedItemResult insertDamagedItem(int companyId, DamagedItem damagedItem) {
        String sql = "INSERT INTO " + TenantSqlIdentifiers.damagedListTable(companyId) + " " +
                "(\"ProductId\", \"ProductName\", \"Time\", \"Reason\", \"Damaged by\", \"Cashier user\", " +
                "\"AmountTP\", \"Paid\", \"branchId\", \"quantity\") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "RETURNING \"DId\"";
        Integer damagedItemId = jdbcTemplate.queryForObject(
                sql,
                Integer.class,
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
        return new AddDamagedItemResult(damagedItemId == null ? 0 : damagedItemId);
    }

    public int decrementProductQuantity(int companyId, int branchId, int productId, int quantity) {
        String sql = "UPDATE " + TenantSqlIdentifiers.inventoryBranchStockBalanceTable(companyId) + " " +
                "SET quantity = quantity - ?, updated_at = CURRENT_TIMESTAMP " +
                "WHERE branch_id = ? AND product_id = ? AND quantity >= ?";
        return jdbcTemplate.update(sql, quantity, branchId, productId, quantity);
    }

    public int insertDamagedInventoryTransaction(int companyId, int branchId, DamagedItem damagedItem) {
        String sql = "INSERT INTO " + TenantSqlIdentifiers.inventoryTransactionsTable(companyId, branchId) + " " +
                "(\"productId\", \"userName\", \"supplierId\", \"transactionType\", \"NumItems\", \"transTotal\", " +
                "\"payType\", \"time\", \"RemainingAmount\") " +
                "VALUES (?, ?, COALESCE((SELECT supplier_id FROM " + TenantSqlIdentifiers.inventoryProductTable(companyId) + " WHERE product_id = ?), 0), ?, ?, ?, ?, ?, ?)";
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

    public AddDamagedLedgerResult insertDamagedLedgerEntry(int companyId, int branchId, DamagedItem damagedItem) {
        String sql = """
                INSERT INTO %s (
                    branch_id, product_id, quantity_delta, movement_type, reference_type, reference_id,
                    actor_name, note, supplier_id, trans_total, pay_type, remaining_amount, created_at
                ) VALUES (
                    :branchId, :productId, :quantityDelta, :movementType, :referenceType, :referenceId,
                    :actorName, :note, :supplierId, :transTotal, :payType, :remainingAmount, :createdAt
                )
                RETURNING stock_ledger_id
                """.formatted(TenantSqlIdentifiers.inventoryStockLedgerTable(companyId));

        Long stockLedgerId = namedParameterJdbcTemplate.queryForObject(
                sql,
                new MapSqlParameterSource()
                        .addValue("branchId", branchId)
                        .addValue("productId", damagedItem.getProductId())
                        .addValue("quantityDelta", damagedItem.getQuantity() * -1)
                        .addValue("movementType", "DAMAGED_OUT")
                        .addValue("referenceType", "DAMAGED_ITEM")
                        .addValue("referenceId", damagedItem.getProductId() + ":" + damagedItem.getTime().getTime())
                        .addValue("actorName", damagedItem.getCashierUser())
                        .addValue("note", damagedItem.getReason())
                        .addValue("supplierId", 0)
                        .addValue("transTotal", damagedItem.getAmountTP())
                        .addValue("payType", damagedItem.isPaid() ? "PaidDamaged" : "Damaged")
                        .addValue("remainingAmount", damagedItem.isPaid() ? 0 : damagedItem.getAmountTP())
                        .addValue("createdAt", damagedItem.getTime()),
                Long.class
        );
        return new AddDamagedLedgerResult(stockLedgerId == null ? 0L : stockLedgerId);
    }

    public record AddDamagedItemResult(int damagedItemId) {}

    public record AddDamagedLedgerResult(long stockLedgerId) {}

    public boolean deleteDamagedItem(int companyId, int branchId, int damagedId) {
        String sql = "DELETE FROM " + TenantSqlIdentifiers.damagedListTable(companyId) +
                " WHERE \"DId\" = ? AND \"branchId\" = ?";
        return jdbcTemplate.update(sql, damagedId, branchId) == 1;
    }

    public boolean updateDamagedItemPaymentStatus(int companyId, int branchId, int damagedId, boolean paid) {
        String sql = "UPDATE " + TenantSqlIdentifiers.damagedListTable(companyId) +
                " SET \"Paid\" = ? WHERE \"DId\" = ? AND \"branchId\" = ?";
        return jdbcTemplate.update(sql, paid, damagedId, branchId) == 1;
    }

    public DamagedItem getDamagedItemById(int companyId, int branchId, int damagedId) {
        String sql = "SELECT \"DId\", \"ProductId\", \"ProductName\", \"Time\", \"Reason\", \"Damaged by\", " +
                "\"Cashier user\", \"AmountTP\", \"Paid\", \"branchId\", \"quantity\" " +
                "FROM " + TenantSqlIdentifiers.damagedListTable(companyId) + " WHERE \"branchId\" = ? AND \"DId\" = ?";
        List<DamagedItem> items = jdbcTemplate.query(sql, DAMAGED_ITEM_ROW_MAPPER, branchId, damagedId);
        return items.isEmpty() ? null : items.get(0);
    }
}
