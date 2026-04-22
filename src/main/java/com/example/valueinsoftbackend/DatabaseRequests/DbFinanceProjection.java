package com.example.valueinsoftbackend.DatabaseRequests;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.UUID;

@Repository
public class DbFinanceProjection {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public DbFinanceProjection(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public int resetAccountBalancesForPeriod(int companyId, UUID fiscalPeriodId, String currencyCode, Integer updatedBy) {
        return namedParameterJdbcTemplate.update(
                "UPDATE public.finance_account_balance " +
                        "SET period_debit = 0, " +
                        "period_credit = 0, " +
                        "closing_debit = opening_debit, " +
                        "closing_credit = opening_credit, " +
                        "last_rebuilt_at = NOW(), " +
                        "updated_by = :updatedBy " +
                        "WHERE company_id = :companyId " +
                        "AND fiscal_period_id = :fiscalPeriodId " +
                        "AND currency_code = :currencyCode",
                params(companyId, fiscalPeriodId, currencyCode).addValue("updatedBy", updatedBy));
    }

    public int rebuildBranchAccountBalances(int companyId, UUID fiscalPeriodId, String currencyCode, Integer updatedBy) {
        return namedParameterJdbcTemplate.update(
                "INSERT INTO public.finance_account_balance " +
                        "(company_id, fiscal_period_id, account_id, branch_id, currency_code, " +
                        "period_debit, period_credit, closing_debit, closing_credit, last_rebuilt_at, created_by, updated_by) " +
                        "SELECT l.company_id, l.fiscal_period_id, l.account_id, l.branch_id, l.currency_code, " +
                        "SUM(l.debit_amount), SUM(l.credit_amount), SUM(l.debit_amount), SUM(l.credit_amount), " +
                        "NOW(), :updatedBy, :updatedBy " +
                        "FROM public.finance_journal_line l " +
                        "JOIN public.finance_journal_entry j " +
                        "ON j.company_id = l.company_id AND j.journal_entry_id = l.journal_entry_id " +
                        "WHERE l.company_id = :companyId " +
                        "AND l.fiscal_period_id = :fiscalPeriodId " +
                        "AND l.currency_code = :currencyCode " +
                        "AND l.branch_id IS NOT NULL " +
                        "AND j.status IN ('posted', 'reversed') " +
                        "GROUP BY l.company_id, l.fiscal_period_id, l.account_id, l.branch_id, l.currency_code " +
                        "ON CONFLICT (company_id, fiscal_period_id, account_id, branch_id, currency_code) " +
                        "WHERE branch_id IS NOT NULL " +
                        "DO UPDATE SET " +
                        "period_debit = EXCLUDED.period_debit, " +
                        "period_credit = EXCLUDED.period_credit, " +
                        "closing_debit = public.finance_account_balance.opening_debit + EXCLUDED.period_debit, " +
                        "closing_credit = public.finance_account_balance.opening_credit + EXCLUDED.period_credit, " +
                        "last_rebuilt_at = NOW(), " +
                        "updated_by = :updatedBy",
                params(companyId, fiscalPeriodId, currencyCode).addValue("updatedBy", updatedBy));
    }

    public int rebuildCompanyAccountBalances(int companyId, UUID fiscalPeriodId, String currencyCode, Integer updatedBy) {
        return namedParameterJdbcTemplate.update(
                "INSERT INTO public.finance_account_balance " +
                        "(company_id, fiscal_period_id, account_id, branch_id, currency_code, " +
                        "period_debit, period_credit, closing_debit, closing_credit, last_rebuilt_at, created_by, updated_by) " +
                        "SELECT l.company_id, l.fiscal_period_id, l.account_id, NULL::INTEGER, l.currency_code, " +
                        "SUM(l.debit_amount), SUM(l.credit_amount), SUM(l.debit_amount), SUM(l.credit_amount), " +
                        "NOW(), :updatedBy, :updatedBy " +
                        "FROM public.finance_journal_line l " +
                        "JOIN public.finance_journal_entry j " +
                        "ON j.company_id = l.company_id AND j.journal_entry_id = l.journal_entry_id " +
                        "WHERE l.company_id = :companyId " +
                        "AND l.fiscal_period_id = :fiscalPeriodId " +
                        "AND l.currency_code = :currencyCode " +
                        "AND j.status IN ('posted', 'reversed') " +
                        "GROUP BY l.company_id, l.fiscal_period_id, l.account_id, l.currency_code " +
                        "ON CONFLICT (company_id, fiscal_period_id, account_id, currency_code) " +
                        "WHERE branch_id IS NULL " +
                        "DO UPDATE SET " +
                        "period_debit = EXCLUDED.period_debit, " +
                        "period_credit = EXCLUDED.period_credit, " +
                        "closing_debit = public.finance_account_balance.opening_debit + EXCLUDED.period_debit, " +
                        "closing_credit = public.finance_account_balance.opening_credit + EXCLUDED.period_credit, " +
                        "last_rebuilt_at = NOW(), " +
                        "updated_by = :updatedBy",
                params(companyId, fiscalPeriodId, currencyCode).addValue("updatedBy", updatedBy));
    }

    public long countAccountBalanceRows(int companyId, UUID fiscalPeriodId, String currencyCode) {
        Long value = namedParameterJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.finance_account_balance " +
                        "WHERE company_id = :companyId " +
                        "AND fiscal_period_id = :fiscalPeriodId " +
                        "AND currency_code = :currencyCode",
                params(companyId, fiscalPeriodId, currencyCode),
                Long.class);
        return value == null ? 0 : value;
    }

    public ProjectionTotals getCompanyProjectionTotals(int companyId, UUID fiscalPeriodId, String currencyCode) {
        return namedParameterJdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(closing_debit), 0) AS total_closing_debit, " +
                        "COALESCE(SUM(closing_credit), 0) AS total_closing_credit " +
                        "FROM public.finance_account_balance " +
                        "WHERE company_id = :companyId " +
                        "AND fiscal_period_id = :fiscalPeriodId " +
                        "AND currency_code = :currencyCode " +
                        "AND branch_id IS NULL",
                params(companyId, fiscalPeriodId, currencyCode),
                (rs, rowNum) -> new ProjectionTotals(
                        rs.getBigDecimal("total_closing_debit"),
                        rs.getBigDecimal("total_closing_credit")));
    }

    private MapSqlParameterSource params(int companyId, UUID fiscalPeriodId, String currencyCode) {
        return new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("fiscalPeriodId", fiscalPeriodId)
                .addValue("currencyCode", currencyCode);
    }

    public record ProjectionTotals(BigDecimal totalClosingDebit, BigDecimal totalClosingCredit) {
    }
}
