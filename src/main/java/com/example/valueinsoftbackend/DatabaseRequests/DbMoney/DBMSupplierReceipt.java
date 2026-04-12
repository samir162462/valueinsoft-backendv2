package com.example.valueinsoftbackend.DatabaseRequests.DbMoney;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Sales.SupplierReceipt;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

@Repository
public class DBMSupplierReceipt {

    private static final RowMapper<SupplierReceipt> supplierReceiptRowMapper = (rs, rowNum) -> new SupplierReceipt(
            rs.getInt("srId"),
            rs.getInt("transId"),
            rs.getBigDecimal("amountPaid"),
            rs.getBigDecimal("remainingAmount"),
            rs.getTimestamp("receiptTime"),
            rs.getString("userRecived"),
            rs.getInt("supplierId"),
            rs.getString("type"),
            rs.getInt("branchId")
    );

    private final JdbcTemplate jdbcTemplate;

    public DBMSupplierReceipt(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<SupplierReceipt> getSupplierReceipts(int companyId, int supplierId) {
        TenantSqlIdentifiers.requirePositive(supplierId, "supplierId");
        String sql = "SELECT \"srId\", \"transId\", \"amountPaid\"::money::numeric AS \"amountPaid\", " +
                "\"remainingAmount\"::money::numeric AS \"remainingAmount\", \"receiptTime\", \"userRecived\", " +
                "\"supplierId\", type, \"branchId\" FROM " + TenantSqlIdentifiers.supplierReceiptsTable(companyId) +
                " WHERE \"supplierId\" = ? ORDER BY \"receiptTime\" DESC";
        return jdbcTemplate.query(sql, supplierReceiptRowMapper, supplierId);
    }

    public int insertSupplierReceipt(int companyId, SupplierReceipt supplierReceipt) {
        String sql = "INSERT INTO " + TenantSqlIdentifiers.supplierReceiptsTable(companyId) +
                " (\"transId\", \"amountPaid\", \"remainingAmount\", \"receiptTime\", \"userRecived\", \"supplierId\", type, \"branchId\") " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        return jdbcTemplate.update(
                sql,
                supplierReceipt.getTransId(),
                supplierReceipt.getAmountPaid(),
                supplierReceipt.getRemainingAmount(),
                new Timestamp(System.currentTimeMillis()),
                supplierReceipt.getUserRecived(),
                supplierReceipt.getSupplierId(),
                supplierReceipt.getType(),
                supplierReceipt.getBranchId()
        );
    }

    public int updateInventoryRemainingAmount(int companyId, int branchId, int transId, java.math.BigDecimal remainingAmount) {
        String sql = "UPDATE " + TenantSqlIdentifiers.inventoryStockLedgerTable(companyId) +
                " SET remaining_amount = ? WHERE branch_id = ? AND stock_ledger_id = ?";
        return jdbcTemplate.update(sql, remainingAmount, branchId, transId);
    }

    public int decrementSupplierRemaining(int companyId, int branchId, int supplierId, java.math.BigDecimal amountPaid) {
        String sql = "UPDATE " + TenantSqlIdentifiers.supplierTable(companyId, branchId) +
                " SET \"supplierRemainig\" = \"supplierRemainig\" - ? WHERE \"supplierId\" = ?";
        return jdbcTemplate.update(sql, amountPaid, supplierId);
    }

    public void verifyDependentRows(int inventoryRows, int supplierRows) {
        if (inventoryRows != 1 || supplierRows != 1) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "SUPPLIER_RECEIPT_DEPENDENCY_NOT_FOUND",
                    "Supplier receipt references a missing transaction or supplier"
            );
        }
    }
}
