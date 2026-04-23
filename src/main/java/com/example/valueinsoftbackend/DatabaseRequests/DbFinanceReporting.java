package com.example.valueinsoftbackend.DatabaseRequests;

import com.example.valueinsoftbackend.Model.Finance.FinanceGeneralLedgerLineItem;
import com.example.valueinsoftbackend.Model.Finance.FinanceStatementLineItem;
import com.example.valueinsoftbackend.Model.Finance.FinanceTrialBalanceLineItem;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.UUID;

@Repository
public class DbFinanceReporting {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public DbFinanceReporting(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public ArrayList<FinanceTrialBalanceLineItem> getTrialBalanceLines(int companyId,
                                                                       UUID fiscalPeriodId,
                                                                       Integer branchId,
                                                                       String currencyCode,
                                                                       boolean includeZeroBalances) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("fiscalPeriodId", fiscalPeriodId)
                .addValue("branchId", branchId)
                .addValue("currencyCode", currencyCode);

        String branchJoin = branchId == null ? "b.branch_id IS NULL " : "b.branch_id = :branchId ";
        String zeroFilter = includeZeroBalances ? "" : "AND b.account_balance_id IS NOT NULL ";

        return new ArrayList<>(namedParameterJdbcTemplate.query(
                "SELECT a.account_id, a.account_code, a.account_name, a.account_type, a.normal_balance, " +
                        "a.account_path, a.account_level, b.branch_id, COALESCE(b.currency_code, :currencyCode) AS currency_code, " +
                        "COALESCE(b.opening_debit, 0) AS opening_debit, " +
                        "COALESCE(b.opening_credit, 0) AS opening_credit, " +
                        "COALESCE(b.period_debit, 0) AS period_debit, " +
                        "COALESCE(b.period_credit, 0) AS period_credit, " +
                        "COALESCE(b.closing_debit, 0) AS closing_debit, " +
                        "COALESCE(b.closing_credit, 0) AS closing_credit " +
                        "FROM public.finance_account a " +
                        "LEFT JOIN public.finance_account_balance b " +
                        "ON b.company_id = a.company_id " +
                        "AND b.account_id = a.account_id " +
                        "AND b.fiscal_period_id = :fiscalPeriodId " +
                        "AND b.currency_code = :currencyCode " +
                        "AND " + branchJoin +
                        "WHERE a.company_id = :companyId " +
                        "AND a.status <> 'archived' " +
                        zeroFilter +
                        "ORDER BY a.account_path ASC, a.account_code ASC",
                params,
                (rs, rowNum) -> mapTrialBalanceLine(rs)));
    }

    public LedgerBalanceValue getLedgerBalance(int companyId,
                                               UUID fiscalPeriodId,
                                               UUID accountId,
                                               Integer branchId,
                                               String currencyCode) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("fiscalPeriodId", fiscalPeriodId)
                .addValue("accountId", accountId)
                .addValue("branchId", branchId)
                .addValue("currencyCode", currencyCode);

        String branchFilter = branchId == null ? "branch_id IS NULL " : "branch_id = :branchId ";

        try {
            return namedParameterJdbcTemplate.queryForObject(
                    "SELECT opening_debit, opening_credit, period_debit, period_credit, closing_debit, closing_credit " +
                            "FROM public.finance_account_balance " +
                            "WHERE company_id = :companyId " +
                            "AND fiscal_period_id = :fiscalPeriodId " +
                            "AND account_id = :accountId " +
                            "AND currency_code = :currencyCode " +
                            "AND " + branchFilter,
                    params,
                    (rs, rowNum) -> new LedgerBalanceValue(
                            rs.getBigDecimal("opening_debit"),
                            rs.getBigDecimal("opening_credit"),
                            rs.getBigDecimal("period_debit"),
                            rs.getBigDecimal("period_credit"),
                            rs.getBigDecimal("closing_debit"),
                            rs.getBigDecimal("closing_credit")));
        } catch (EmptyResultDataAccessException exception) {
            BigDecimal zero = BigDecimal.ZERO.setScale(4);
            return new LedgerBalanceValue(zero, zero, zero, zero, zero, zero);
        }
    }

    public ArrayList<FinanceGeneralLedgerLineItem> getGeneralLedgerLines(int companyId,
                                                                         UUID fiscalPeriodId,
                                                                         UUID accountId,
                                                                         Integer branchId,
                                                                         String currencyCode,
                                                                         BigDecimal openingNormalBalance,
                                                                         int limit,
                                                                         int offset) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("fiscalPeriodId", fiscalPeriodId)
                .addValue("accountId", accountId)
                .addValue("branchId", branchId)
                .addValue("currencyCode", currencyCode)
                .addValue("openingNormalBalance", openingNormalBalance)
                .addValue("limit", limit)
                .addValue("offset", offset);

        String branchFilter = branchId == null ? "" : "AND l.branch_id = :branchId ";

        return new ArrayList<>(namedParameterJdbcTemplate.query(
                "SELECT l.journal_line_id, l.journal_entry_id, j.journal_number, j.journal_type, " +
                        "j.status AS journal_status, l.line_number, l.posting_date, l.fiscal_period_id, " +
                        "l.account_id, a.account_code, a.account_name, l.branch_id, l.debit_amount, l.credit_amount, " +
                        "(:openingNormalBalance + SUM(CASE WHEN a.normal_balance = 'credit' " +
                        "THEN l.credit_amount - l.debit_amount ELSE l.debit_amount - l.credit_amount END) " +
                        "OVER (ORDER BY l.posting_date ASC, j.journal_number ASC, l.line_number ASC, l.journal_line_id ASC " +
                        "ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW)) AS running_balance, " +
                        "l.currency_code, l.description, l.source_module, l.source_type, l.source_id, " +
                        "l.cost_center_id, cc.code AS cost_center_code, cc.name AS cost_center_name, l.tax_code_id, tc.code AS tax_code " +
                        "FROM public.finance_journal_line l " +
                        "JOIN public.finance_journal_entry j " +
                        "ON j.company_id = l.company_id AND j.journal_entry_id = l.journal_entry_id " +
                        "JOIN public.finance_account a " +
                        "ON a.company_id = l.company_id AND a.account_id = l.account_id " +
                        "LEFT JOIN public.finance_cost_center cc " +
                        "ON cc.company_id = l.company_id AND cc.cost_center_id = l.cost_center_id " +
                        "LEFT JOIN public.finance_tax_code tc " +
                        "ON tc.company_id = l.company_id AND tc.tax_code_id = l.tax_code_id " +
                        "WHERE l.company_id = :companyId " +
                        "AND l.fiscal_period_id = :fiscalPeriodId " +
                        "AND l.account_id = :accountId " +
                        "AND l.currency_code = :currencyCode " +
                        "AND j.status IN ('posted', 'reversed') " +
                        branchFilter +
                        "ORDER BY l.posting_date ASC, j.journal_number ASC, l.line_number ASC, l.journal_line_id ASC " +
                        "LIMIT :limit OFFSET :offset",
                params,
                (rs, rowNum) -> mapGeneralLedgerLine(rs)));
    }

    public ArrayList<FinanceStatementLineItem> getStatementLines(int companyId,
                                                                 UUID fiscalPeriodId,
                                                                 Integer branchId,
                                                                 String currencyCode,
                                                                 ArrayList<String> accountTypes,
                                                                 boolean includeZeroBalances) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("fiscalPeriodId", fiscalPeriodId)
                .addValue("branchId", branchId)
                .addValue("currencyCode", currencyCode)
                .addValue("accountTypes", accountTypes);

        String branchJoin = branchId == null ? "b.branch_id IS NULL " : "b.branch_id = :branchId ";
        String zeroFilter = includeZeroBalances ? "" : "AND b.account_balance_id IS NOT NULL ";

        return new ArrayList<>(namedParameterJdbcTemplate.query(
                "SELECT a.account_id, a.account_code, a.account_name, a.account_type, a.normal_balance, " +
                        "a.account_path, a.account_level, " +
                        "CASE WHEN a.normal_balance = 'credit' " +
                        "THEN COALESCE(b.closing_credit, 0) - COALESCE(b.closing_debit, 0) " +
                        "ELSE COALESCE(b.closing_debit, 0) - COALESCE(b.closing_credit, 0) END AS amount " +
                        "FROM public.finance_account a " +
                        "LEFT JOIN public.finance_account_balance b " +
                        "ON b.company_id = a.company_id " +
                        "AND b.account_id = a.account_id " +
                        "AND b.fiscal_period_id = :fiscalPeriodId " +
                        "AND b.currency_code = :currencyCode " +
                        "AND " + branchJoin +
                        "WHERE a.company_id = :companyId " +
                        "AND a.account_type IN (:accountTypes) " +
                        "AND a.status <> 'archived' " +
                        zeroFilter +
                        "ORDER BY a.account_type ASC, a.account_path ASC, a.account_code ASC",
                params,
                (rs, rowNum) -> mapStatementLine(rs)));
    }

    private FinanceTrialBalanceLineItem mapTrialBalanceLine(ResultSet rs) throws SQLException {
        BigDecimal closingDebit = rs.getBigDecimal("closing_debit");
        BigDecimal closingCredit = rs.getBigDecimal("closing_credit");
        String normalBalance = rs.getString("normal_balance");
        BigDecimal normalBalanceAmount = "credit".equals(normalBalance)
                ? closingCredit.subtract(closingDebit)
                : closingDebit.subtract(closingCredit);

        return new FinanceTrialBalanceLineItem(
                rs.getObject("account_id", UUID.class),
                rs.getString("account_code"),
                rs.getString("account_name"),
                rs.getString("account_type"),
                normalBalance,
                rs.getString("account_path"),
                rs.getInt("account_level"),
                nullableInteger(rs, "branch_id"),
                rs.getString("currency_code"),
                rs.getBigDecimal("opening_debit"),
                rs.getBigDecimal("opening_credit"),
                rs.getBigDecimal("period_debit"),
                rs.getBigDecimal("period_credit"),
                closingDebit,
                closingCredit,
                normalBalanceAmount);
    }

    private FinanceGeneralLedgerLineItem mapGeneralLedgerLine(ResultSet rs) throws SQLException {
        return new FinanceGeneralLedgerLineItem(
                rs.getObject("journal_line_id", UUID.class),
                rs.getObject("journal_entry_id", UUID.class),
                rs.getString("journal_number"),
                rs.getString("journal_type"),
                rs.getString("journal_status"),
                rs.getInt("line_number"),
                rs.getDate("posting_date").toLocalDate(),
                rs.getObject("fiscal_period_id", UUID.class),
                rs.getObject("account_id", UUID.class),
                rs.getString("account_code"),
                rs.getString("account_name"),
                nullableInteger(rs, "branch_id"),
                rs.getBigDecimal("debit_amount"),
                rs.getBigDecimal("credit_amount"),
                rs.getBigDecimal("running_balance"),
                rs.getString("currency_code"),
                rs.getString("description"),
                rs.getString("source_module"),
                rs.getString("source_type"),
                rs.getString("source_id"),
                rs.getObject("cost_center_id", UUID.class),
                rs.getString("cost_center_code"),
                rs.getString("cost_center_name"),
                rs.getObject("tax_code_id", UUID.class),
                rs.getString("tax_code"));
    }

    private FinanceStatementLineItem mapStatementLine(ResultSet rs) throws SQLException {
        return new FinanceStatementLineItem(
                rs.getObject("account_id", UUID.class),
                rs.getString("account_code"),
                rs.getString("account_name"),
                rs.getString("account_type"),
                rs.getString("normal_balance"),
                rs.getString("account_path"),
                rs.getInt("account_level"),
                rs.getBigDecimal("amount"));
    }

    private Integer nullableInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    public record LedgerBalanceValue(BigDecimal openingDebit,
                                     BigDecimal openingCredit,
                                     BigDecimal periodDebit,
                                     BigDecimal periodCredit,
                                     BigDecimal closingDebit,
                                     BigDecimal closingCredit) {
    }
}
