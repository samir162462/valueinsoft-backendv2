package com.example.valueinsoftbackend.DatabaseRequests;

import com.example.valueinsoftbackend.Model.Finance.DailyCashClosingCashMovementRow;
import com.example.valueinsoftbackend.Model.Finance.DailyCashClosingExpenseRow;
import com.example.valueinsoftbackend.Model.Finance.DailyCashClosingHeader;
import com.example.valueinsoftbackend.Model.Finance.DailyCashClosingInvoiceRow;
import com.example.valueinsoftbackend.Model.Finance.DailyCashClosingPaymentBreakdownRow;
import com.example.valueinsoftbackend.Model.Request.Finance.DailyCashClosingReportRequest;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

@Repository
public class DbFinanceDailyCashClosingReport {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public DbFinanceDailyCashClosingReport(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public boolean branchBelongsToCompany(int companyId, int branchId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("branchId", branchId);
        Boolean exists = namedParameterJdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM public.\"Branch\" WHERE \"companyId\" = :companyId AND \"branchId\" = :branchId)",
                params,
                Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    public DailyCashClosingHeader fetchHeader(DailyCashClosingReportRequest request) {
        MapSqlParameterSource params = baseParams(request);
        String userTable = TenantSqlIdentifiers.userTable(request.getCompanyId());
        String shiftTable = TenantSqlIdentifiers.shiftPeriodTable(request.getCompanyId());

        try {
            return namedParameterJdbcTemplate.queryForObject(
                    """
                    SELECT c."companyName" AS company_name,
                           b."branchName" AS branch_name,
                           (
                               SELECT COALESCE(NULLIF(TRIM(CONCAT_WS(' ', u."firstName", u."lastName")), ''), u."userName")
                               FROM %s u
                               WHERE u."userName" = :cashierId
                               LIMIT 1
                           ) AS cashier_name,
                           (
                               SELECT CONCAT('Shift #', sh."PosSOID", ' - ', COALESCE(sh.status, 'unknown'))
                               FROM %s sh
                               WHERE sh."PosSOID" = :shiftId
                               LIMIT 1
                           ) AS shift_label
                    FROM public."Company" c
                    JOIN public."Branch" b ON b."companyId" = c.id
                    WHERE c.id = :companyId
                      AND b."branchId" = :branchId
                    """.formatted(userTable, shiftTable),
                    params,
                    (rs, rowNum) -> new DailyCashClosingHeader(
                            rs.getString("company_name"),
                            rs.getString("branch_name"),
                            rs.getString("cashier_name"),
                            rs.getString("shift_label")));
        } catch (EmptyResultDataAccessException exception) {
            return new DailyCashClosingHeader("", "", null, null);
        }
    }

    public SalesTotals fetchSalesTotals(DailyCashClosingReportRequest request) {
        MapSqlParameterSource params = baseParams(request);
        String sql = """
                SELECT COUNT(*) AS invoice_count,
                       COALESCE(SUM(COALESCE(o."orderTotal", 0)::numeric), 0) AS gross_sales,
                       COALESCE(SUM(COALESCE(o."orderDiscount", 0)::numeric), 0) AS discounts,
                       COALESCE(SUM(COALESCE(o."orderBouncedBack", 0)::numeric), 0) AS returns_amount,
                       COALESCE(SUM(COALESCE(o."orderIncome", o."orderTotal" - COALESCE(o."orderDiscount", 0), 0)::numeric), 0) AS net_sales,
                       COALESCE(SUM(CASE WHEN COALESCE(o."orderBouncedBack", 0)::numeric > 0 THEN 1 ELSE 0 END), 0) AS returns_count
                FROM %s o
                LEFT JOIN %s sh ON sh."PosSOID" = o.shift_id
                WHERE %s
                """.formatted(orderTable(request), shiftTable(request), orderWhereClause());

        return namedParameterJdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new SalesTotals(
                rs.getLong("invoice_count"),
                money(rs, "gross_sales"),
                money(rs, "discounts"),
                money(rs, "returns_amount"),
                money(rs, "net_sales"),
                rs.getLong("returns_count")));
    }

    public ArrayList<DailyCashClosingPaymentBreakdownRow> fetchPaymentBreakdown(DailyCashClosingReportRequest request) {
        MapSqlParameterSource params = baseParams(request);
        String sql = """
                SELECT COALESCE(NULLIF(o."orderType", ''), 'Unknown') AS payment_method,
                       COUNT(*) AS invoice_count,
                       COALESCE(SUM(COALESCE(o."orderTotal", 0)::numeric), 0) AS gross_amount,
                       COALESCE(SUM(COALESCE(o."orderDiscount", 0)::numeric), 0) AS discount_amount,
                       COALESCE(SUM(COALESCE(o."orderBouncedBack", 0)::numeric), 0) AS return_amount,
                       COALESCE(SUM(COALESCE(o."orderIncome", o."orderTotal" - COALESCE(o."orderDiscount", 0), 0)::numeric), 0) AS net_amount
                FROM %s o
                LEFT JOIN %s sh ON sh."PosSOID" = o.shift_id
                WHERE %s
                GROUP BY COALESCE(NULLIF(o."orderType", ''), 'Unknown')
                ORDER BY payment_method
                """.formatted(orderTable(request), shiftTable(request), orderWhereClause());

        return new ArrayList<>(namedParameterJdbcTemplate.query(sql, params, (rs, rowNum) ->
                new DailyCashClosingPaymentBreakdownRow(
                        rs.getString("payment_method"),
                        rs.getLong("invoice_count"),
                        money(rs, "gross_amount"),
                        money(rs, "discount_amount"),
                        money(rs, "return_amount"),
                        money(rs, "net_amount"))));
    }

    public ArrayList<DailyCashClosingCashMovementRow> fetchCashMovements(DailyCashClosingReportRequest request) {
        MapSqlParameterSource params = baseParams(request);
        String sql = """
                SELECT m.movement_type,
                       COUNT(*) AS movement_count,
                       COALESCE(SUM(m.amount), 0) AS amount
                FROM %s m
                LEFT JOIN %s sh ON sh."PosSOID" = m.shift_id
                WHERE m.branch_id = :branchId
                  AND m.created_at >= :fromTs
                  AND m.created_at < :toTs
                  %s
                  %s
                  %s
                GROUP BY m.movement_type
                ORDER BY m.movement_type
                """.formatted(
                TenantSqlIdentifiers.shiftCashMovementTable(request.getCompanyId()),
                shiftTable(request),
                request.getShiftId() == null ? "" : "AND m.shift_id = :shiftId",
                isBlank(request.getCashierId()) ? "" : "AND (m.actor_user_id = :cashierId OR m.associated_user_id = :cashierId)",
                isBlank(request.getStatus()) ? "" : "AND LOWER(COALESCE(sh.status, '')) = :status");

        return new ArrayList<>(namedParameterJdbcTemplate.query(sql, params, (rs, rowNum) ->
                new DailyCashClosingCashMovementRow(
                        rs.getString("movement_type"),
                        rs.getLong("movement_count"),
                        money(rs, "amount"))));
    }

    public ShiftTotals fetchShiftTotals(DailyCashClosingReportRequest request) {
        MapSqlParameterSource params = baseParams(request);
        String sql = """
                SELECT COALESCE(SUM(sh.opening_float), 0) AS opening_float,
                       COALESCE(SUM(sh.expected_cash), 0) AS expected_cash,
                       COALESCE(SUM(sh.counted_cash), 0) AS counted_cash,
                       COALESCE(SUM(sh.variance_amount), 0) AS variance_amount,
                       COUNT(*) AS shift_count
                FROM %s sh
                WHERE sh."branchId" = :branchId
                  AND sh."ShiftStartTime" < :toTs
                  AND COALESCE(sh."ShiftEndTime", :toTs) >= :fromTs
                  %s
                  %s
                  %s
                """.formatted(
                shiftTable(request),
                request.getShiftId() == null ? "" : "AND sh.\"PosSOID\" = :shiftId",
                isBlank(request.getCashierId()) ? "" : "AND (sh.assigned_cashier_id = :cashierId OR sh.opened_by_user_id = :cashierId OR sh.closed_by_user_id = :cashierId)",
                isBlank(request.getStatus()) ? "" : "AND LOWER(COALESCE(sh.status, '')) = :status");

        return namedParameterJdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new ShiftTotals(
                money(rs, "opening_float"),
                money(rs, "expected_cash"),
                money(rs, "counted_cash"),
                money(rs, "variance_amount"),
                rs.getLong("shift_count")));
    }

    public BigDecimal fetchExpenseTotal(DailyCashClosingReportRequest request) {
        MapSqlParameterSource params = baseParams(request);
        String sql = """
                SELECT COALESCE(SUM(e.amount::money::numeric), 0) AS amount
                FROM %s e
                WHERE e."branchId" = :branchId
                  AND e."time" >= :fromTs
                  AND e."time" < :toTs
                  %s
                  %s
                """.formatted(
                TenantSqlIdentifiers.expensesTable(request.getCompanyId(), false),
                isBlank(request.getCashierId()) ? "" : "AND e.\"user\" = :cashierId",
                request.getShiftId() == null ? "" : shiftExpenseExistsClause(request));

        return namedParameterJdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> money(rs, "amount"));
    }

    public ArrayList<DailyCashClosingInvoiceRow> fetchInvoices(DailyCashClosingReportRequest request, int limit) {
        MapSqlParameterSource params = baseParams(request).addValue("limit", limit);
        String sql = """
                SELECT o."orderId" AS invoice_no,
                       o."orderTime" AS date_time,
                       COALESCE(NULLIF(TRIM(CONCAT_WS(' ', u."firstName", u."lastName")), ''), o."salesUser") AS cashier,
                       COALESCE(NULLIF(o."clientName", ''), c."clientName", 'Walk-in') AS customer,
                       COALESCE(NULLIF(o."orderType", ''), 'Unknown') AS payment_method,
                       COALESCE(o."orderTotal", 0)::numeric AS gross_amount,
                       COALESCE(o."orderDiscount", 0)::numeric AS discount_amount,
                       COALESCE(o."orderBouncedBack", 0)::numeric AS return_amount,
                       COALESCE(o."orderIncome", o."orderTotal" - COALESCE(o."orderDiscount", 0), 0)::numeric AS net_amount,
                       COALESCE(sh.status, 'completed') AS status
                FROM %s o
                LEFT JOIN %s sh ON sh."PosSOID" = o.shift_id
                LEFT JOIN %s u ON u."userName" = o."salesUser"
                LEFT JOIN %s c ON c.c_id = o."clientId"
                WHERE %s
                ORDER BY o."orderTime" ASC, o."orderId" ASC
                LIMIT :limit
                """.formatted(orderTable(request), shiftTable(request), TenantSqlIdentifiers.userTable(request.getCompanyId()),
                TenantSqlIdentifiers.clientTable(request.getCompanyId()), orderWhereClause());

        return new ArrayList<>(namedParameterJdbcTemplate.query(sql, params, this::mapInvoice));
    }

    public ArrayList<DailyCashClosingExpenseRow> fetchExpenses(DailyCashClosingReportRequest request, int limit) {
        MapSqlParameterSource params = baseParams(request).addValue("limit", limit);
        String sql = """
                SELECT e."eId" AS expense_no,
                       COALESCE(NULLIF(e.type, ''), 'Expense') AS expense_type,
                       e."time" AS date_time,
                       COALESCE(NULLIF(TRIM(CONCAT_WS(' ', u."firstName", u."lastName")), ''), e."user") AS paid_by,
                       COALESCE(a.account_name, '') AS payment_method,
                       e.amount::money::numeric AS amount,
                       COALESCE(e.name, '') AS notes
                FROM %s e
                LEFT JOIN %s u ON u."userName" = e."user"
                LEFT JOIN public.finance_account a ON a.company_id = :companyId AND a.account_id = e.payment_account_id
                WHERE e."branchId" = :branchId
                  AND e."time" >= :fromTs
                  AND e."time" < :toTs
                  %s
                  %s
                  %s
                ORDER BY e."time" ASC, e."eId" ASC
                LIMIT :limit
                """.formatted(
                TenantSqlIdentifiers.expensesTable(request.getCompanyId(), false),
                TenantSqlIdentifiers.userTable(request.getCompanyId()),
                isBlank(request.getCashierId()) ? "" : "AND e.\"user\" = :cashierId",
                request.getShiftId() == null ? "" : shiftExpenseExistsClause(request),
                isBlank(request.getPaymentMethod()) ? "" : "AND LOWER(COALESCE(a.account_name, '')) = :paymentMethod");

        return new ArrayList<>(namedParameterJdbcTemplate.query(sql, params, this::mapExpense));
    }

    private DailyCashClosingInvoiceRow mapInvoice(ResultSet rs, int rowNum) throws SQLException {
        Timestamp timestamp = rs.getTimestamp("date_time");
        return new DailyCashClosingInvoiceRow(
                rs.getInt("invoice_no"),
                timestamp == null ? null : timestamp.toLocalDateTime(),
                rs.getString("cashier"),
                rs.getString("customer"),
                rs.getString("payment_method"),
                money(rs, "gross_amount"),
                money(rs, "discount_amount"),
                money(rs, "return_amount"),
                money(rs, "net_amount"),
                rs.getString("status"));
    }

    private DailyCashClosingExpenseRow mapExpense(ResultSet rs, int rowNum) throws SQLException {
        Timestamp timestamp = rs.getTimestamp("date_time");
        return new DailyCashClosingExpenseRow(
                rs.getInt("expense_no"),
                rs.getString("expense_type"),
                timestamp == null ? null : timestamp.toLocalDateTime(),
                rs.getString("paid_by"),
                rs.getString("payment_method"),
                money(rs, "amount"),
                rs.getString("notes"));
    }

    private MapSqlParameterSource baseParams(DailyCashClosingReportRequest request) {
        return new MapSqlParameterSource()
                .addValue("companyId", request.getCompanyId())
                .addValue("branchId", request.getBranchId())
                .addValue("fromTs", Timestamp.valueOf(request.getDateFrom().atStartOfDay()))
                .addValue("toTs", Timestamp.valueOf(request.getDateTo().plusDays(1).atStartOfDay()))
                .addValue("cashierId", trimToNull(request.getCashierId()))
                .addValue("shiftId", request.getShiftId())
                .addValue("paymentMethod", lowerTrim(request.getPaymentMethod()))
                .addValue("status", lowerTrim(request.getStatus()));
    }

    private String orderWhereClause() {
        List<String> clauses = new ArrayList<>();
        clauses.add("o.\"orderTime\" >= :fromTs");
        clauses.add("o.\"orderTime\" < :toTs");
        clauses.add("(:cashierId IS NULL OR o.\"salesUser\" = :cashierId)");
        clauses.add("(:shiftId IS NULL OR o.shift_id = :shiftId)");
        clauses.add("(:paymentMethod IS NULL OR LOWER(COALESCE(o.\"orderType\", '')) = :paymentMethod)");
        clauses.add("(:status IS NULL OR LOWER(COALESCE(sh.status, '')) = :status)");
        return String.join(" AND ", clauses);
    }

    private String shiftExpenseExistsClause(DailyCashClosingReportRequest request) {
        return """
                AND EXISTS (
                      SELECT 1
                      FROM %s sx
                      WHERE sx."PosSOID" = :shiftId
                        AND sx."branchId" = :branchId
                        AND e."time" >= sx."ShiftStartTime"
                        AND e."time" <= COALESCE(sx."ShiftEndTime", :toTs)
                  )
                """.formatted(shiftTable(request));
    }

    private String orderTable(DailyCashClosingReportRequest request) {
        return TenantSqlIdentifiers.orderTable(request.getCompanyId(), request.getBranchId());
    }

    private String shiftTable(DailyCashClosingReportRequest request) {
        return TenantSqlIdentifiers.shiftPeriodTable(request.getCompanyId());
    }

    private BigDecimal money(ResultSet rs, String column) throws SQLException {
        BigDecimal value = rs.getBigDecimal(column);
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private static String lowerTrim(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toLowerCase();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public record SalesTotals(long invoiceCount,
                              BigDecimal grossSales,
                              BigDecimal discounts,
                              BigDecimal returnsAmount,
                              BigDecimal netSales,
                              long returnsCount) {
    }

    public record ShiftTotals(BigDecimal openingFloat,
                              BigDecimal expectedCash,
                              BigDecimal countedCash,
                              BigDecimal varianceAmount,
                              long shiftCount) {
    }
}
