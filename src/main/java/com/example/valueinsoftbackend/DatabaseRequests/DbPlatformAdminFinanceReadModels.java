package com.example.valueinsoftbackend.DatabaseRequests;

import com.example.valueinsoftbackend.Model.Expenses;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformClientReceiptsPageResponse;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformExpensesPageResponse;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformSupplierReceiptsPageResponse;
import com.example.valueinsoftbackend.Model.Sales.ClientReceipt;
import com.example.valueinsoftbackend.Model.Sales.SupplierReceipt;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;

@Repository
public class DbPlatformAdminFinanceReadModels {

    private static final RowMapper<Expenses> EXPENSES_ROW_MAPPER = (rs, rowNum) -> new Expenses(
            rs.getInt("eId"),
            rs.getString("type"),
            rs.getBigDecimal("amount"),
            rs.getTimestamp("time"),
            rs.getInt("branchId"),
            rs.getString("user"),
            rs.getString("name"),
            rs.getString("period"),
            rs.getObject("expense_account_id", java.util.UUID.class),
            rs.getObject("payment_account_id", java.util.UUID.class),
            rs.getObject("posted_journal_entry_id", java.util.UUID.class),
            rs.getObject("next_due_date") != null ? rs.getDate("next_due_date").toLocalDate() : null
    );

    private static final RowMapper<ClientReceipt> CLIENT_RECEIPT_ROW_MAPPER = (rs, rowNum) -> new ClientReceipt(
            rs.getInt("crId"),
            rs.getString("type"),
            rs.getBigDecimal("amount"),
            rs.getTimestamp("time"),
            rs.getString("userName"),
            rs.getInt("clientId"),
            rs.getInt("branchId")
    );

    private static final RowMapper<SupplierReceipt> SUPPLIER_RECEIPT_ROW_MAPPER = (rs, rowNum) -> new SupplierReceipt(
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

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public DbPlatformAdminFinanceReadModels(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public PlatformExpensesPageResponse getCompanyExpenses(int companyId, Integer branchId, int page, int size) {
        MapSqlParameterSource params = buildPageParams(branchId, page, size);
        String whereClause = buildBranchWhereClause(branchId);
        String table = TenantSqlIdentifiers.expensesTable(companyId, false);
        String countSql = "SELECT COUNT(*) FROM " + table + whereClause;
        String dataSql = "SELECT \"eId\", type, amount::money::numeric AS amount, \"time\", \"branchId\", \"user\", name, period, " +
                "expense_account_id, payment_account_id, posted_journal_entry_id, next_due_date " +
                "FROM " + table + whereClause +
                " ORDER BY \"time\" DESC, \"eId\" DESC LIMIT :limit OFFSET :offset";

        long totalItems = getCount(countSql, params);
        ArrayList<Expenses> items = new ArrayList<>(
                namedParameterJdbcTemplate.query(dataSql, params, EXPENSES_ROW_MAPPER)
        );
        return new PlatformExpensesPageResponse(items, page, size, totalItems, computeTotalPages(totalItems, size));
    }

    public PlatformClientReceiptsPageResponse getCompanyClientReceipts(int companyId, Integer branchId, int page, int size) {
        MapSqlParameterSource params = buildPageParams(branchId, page, size);
        String whereClause = buildBranchWhereClause(branchId);
        String table = TenantSqlIdentifiers.clientReceiptsTable(companyId);
        String countSql = "SELECT COUNT(*) FROM " + table + whereClause;
        String dataSql = "SELECT \"crId\", type, amount::money::numeric AS amount, \"time\", \"userName\", \"clientId\", \"branchId\" " +
                "FROM " + table + whereClause +
                " ORDER BY \"time\" DESC, \"crId\" DESC LIMIT :limit OFFSET :offset";

        long totalItems = getCount(countSql, params);
        ArrayList<ClientReceipt> items = new ArrayList<>(
                namedParameterJdbcTemplate.query(dataSql, params, CLIENT_RECEIPT_ROW_MAPPER)
        );
        return new PlatformClientReceiptsPageResponse(items, page, size, totalItems, computeTotalPages(totalItems, size));
    }

    public PlatformSupplierReceiptsPageResponse getCompanySupplierReceipts(int companyId, Integer branchId, int page, int size) {
        MapSqlParameterSource params = buildPageParams(branchId, page, size);
        String whereClause = buildBranchWhereClause(branchId);
        String table = TenantSqlIdentifiers.supplierReceiptsTable(companyId);
        String countSql = "SELECT COUNT(*) FROM " + table + whereClause;
        String dataSql = "SELECT \"srId\", \"transId\", \"amountPaid\"::money::numeric AS \"amountPaid\", " +
                "\"remainingAmount\"::money::numeric AS \"remainingAmount\", \"receiptTime\", \"userRecived\", " +
                "\"supplierId\", type, \"branchId\" FROM " + table + whereClause +
                " ORDER BY \"receiptTime\" DESC, \"srId\" DESC LIMIT :limit OFFSET :offset";

        long totalItems = getCount(countSql, params);
        ArrayList<SupplierReceipt> items = new ArrayList<>(
                namedParameterJdbcTemplate.query(dataSql, params, SUPPLIER_RECEIPT_ROW_MAPPER)
        );
        return new PlatformSupplierReceiptsPageResponse(items, page, size, totalItems, computeTotalPages(totalItems, size));
    }

    private MapSqlParameterSource buildPageParams(Integer branchId, int page, int size) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", size)
                .addValue("offset", (page - 1) * size);
        if (branchId != null) {
            params.addValue("branchId", branchId);
        }
        return params;
    }

    private String buildBranchWhereClause(Integer branchId) {
        return branchId == null ? "" : " WHERE \"branchId\" = :branchId";
    }

    private long getCount(String sql, MapSqlParameterSource params) {
        Integer count = namedParameterJdbcTemplate.queryForObject(sql, params, Integer.class);
        return count == null ? 0L : count.longValue();
    }

    private int computeTotalPages(long totalItems, int size) {
        if (totalItems == 0) {
            return 0;
        }
        return (int) Math.ceil((double) totalItems / (double) size);
    }
}
