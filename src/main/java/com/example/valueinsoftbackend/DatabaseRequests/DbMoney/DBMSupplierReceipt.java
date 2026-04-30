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
            rs.getInt("branchId"),
            rs.getString("postingStatus"),
            nullableUuid(rs, "postingRequestId"),
            nullableUuid(rs, "journalId"),
            rs.getString("postingFailureReason")
    );

    private final JdbcTemplate jdbcTemplate;

    public DBMSupplierReceipt(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<SupplierReceipt> getSupplierReceipts(int companyId, int supplierId) {
        TenantSqlIdentifiers.requirePositive(supplierId, "supplierId");
        String sql = "SELECT receipt.\"srId\", receipt.\"transId\", receipt.\"amountPaid\"::money::numeric AS \"amountPaid\", " +
                "receipt.\"remainingAmount\"::money::numeric AS \"remainingAmount\", receipt.\"receiptTime\", receipt.\"userRecived\", " +
                "receipt.\"supplierId\", receipt.type, receipt.\"branchId\", " +
                "fp.status AS \"postingStatus\", fp.posting_request_id AS \"postingRequestId\", " +
                "fp.journal_entry_id AS \"journalId\", fp.last_error AS \"postingFailureReason\" " +
                "FROM " + TenantSqlIdentifiers.supplierReceiptsTable(companyId) + " receipt " +
                "LEFT JOIN public.finance_posting_request fp " +
                "  ON fp.company_id = ? " +
                " AND fp.branch_id = receipt.\"branchId\" " +
                " AND fp.source_module = 'payment' " +
                " AND fp.source_type = 'supplier_payment' " +
                " AND fp.source_id = 'supplier-receipt-' || receipt.\"srId\"::text " +
                "WHERE receipt.\"supplierId\" = ? ORDER BY receipt.\"receiptTime\" DESC";
        return jdbcTemplate.query(sql, supplierReceiptRowMapper, companyId, supplierId);
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

    public SupplierReceipt createSupplierReceipt(int companyId, SupplierReceipt supplierReceipt) {
        String sql = "INSERT INTO " + TenantSqlIdentifiers.supplierReceiptsTable(companyId) +
                " (\"transId\", \"amountPaid\", \"remainingAmount\", \"receiptTime\", \"userRecived\", \"supplierId\", type, \"branchId\") " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                "RETURNING \"srId\", \"transId\", \"amountPaid\"::money::numeric AS \"amountPaid\", " +
                "\"remainingAmount\"::money::numeric AS \"remainingAmount\", \"receiptTime\", \"userRecived\", " +
                "\"supplierId\", type, \"branchId\", NULL::varchar AS \"postingStatus\", " +
                "NULL::uuid AS \"postingRequestId\", NULL::uuid AS \"journalId\", NULL::text AS \"postingFailureReason\"";

        return jdbcTemplate.queryForObject(
                sql,
                supplierReceiptRowMapper,
                supplierReceipt.getTransId(),
                supplierReceipt.getAmountPaid(),
                supplierReceipt.getRemainingAmount(),
                supplierReceipt.getReceiptTime(),
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

    private static java.util.UUID nullableUuid(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        return rs.getObject(column, java.util.UUID.class);
    }
}
