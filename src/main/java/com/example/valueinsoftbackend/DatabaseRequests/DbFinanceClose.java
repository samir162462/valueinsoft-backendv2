package com.example.valueinsoftbackend.DatabaseRequests;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.UUID;

@Repository
public class DbFinanceClose {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public DbFinanceClose(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public long countPostedJournals(int companyId, UUID fiscalPeriodId) {
        return count(
                "SELECT COUNT(*) FROM public.finance_journal_entry " +
                        "WHERE company_id = :companyId " +
                        "AND fiscal_period_id = :fiscalPeriodId " +
                        "AND status IN ('posted', 'reversed')",
                params(companyId, fiscalPeriodId));
    }

    public long countUnpostedJournals(int companyId, UUID fiscalPeriodId) {
        return count(
                "SELECT COUNT(*) FROM public.finance_journal_entry " +
                        "WHERE company_id = :companyId " +
                        "AND fiscal_period_id = :fiscalPeriodId " +
                        "AND status IN ('draft', 'validated')",
                params(companyId, fiscalPeriodId));
    }

    public long countUnbalancedPostedJournals(int companyId, UUID fiscalPeriodId) {
        return count(
                "SELECT COUNT(*) FROM public.finance_journal_entry " +
                        "WHERE company_id = :companyId " +
                        "AND fiscal_period_id = :fiscalPeriodId " +
                        "AND status IN ('posted', 'reversed') " +
                        "AND total_debit <> total_credit",
                params(companyId, fiscalPeriodId));
    }

    public void lockFiscalPeriodForCloseWork(int companyId, UUID fiscalPeriodId) {
        namedParameterJdbcTemplate.queryForObject(
                "SELECT fiscal_period_id FROM public.finance_fiscal_period " +
                        "WHERE company_id = :companyId " +
                        "AND fiscal_period_id = :fiscalPeriodId " +
                        "FOR UPDATE",
                params(companyId, fiscalPeriodId),
                UUID.class);
    }

    public long countOpenPostingRequests(int companyId, UUID fiscalPeriodId) {
        return count(
                "SELECT COUNT(*) FROM public.finance_posting_request pr " +
                        "LEFT JOIN public.finance_journal_entry j " +
                        "ON j.company_id = pr.company_id AND j.journal_entry_id = pr.journal_entry_id " +
                        "WHERE pr.company_id = :companyId " +
                        "AND (pr.fiscal_period_id = :fiscalPeriodId OR j.fiscal_period_id = :fiscalPeriodId) " +
                        "AND pr.status IN ('pending', 'processing', 'failed')",
                params(companyId, fiscalPeriodId));
    }

    public long countOpenPostingBatches(int companyId, UUID fiscalPeriodId) {
        return count(
                "SELECT COUNT(DISTINCT b.posting_batch_id) FROM public.finance_posting_batch b " +
                        "JOIN public.finance_journal_entry j " +
                        "ON j.company_id = b.company_id AND j.posting_batch_id = b.posting_batch_id " +
                        "WHERE b.company_id = :companyId " +
                        "AND j.fiscal_period_id = :fiscalPeriodId " +
                        "AND b.status IN ('pending', 'processing', 'failed', 'partially_posted')",
                params(companyId, fiscalPeriodId));
    }

    public long countCompanyBalanceRows(int companyId, UUID fiscalPeriodId, String currencyCode) {
        return count(
                "SELECT COUNT(*) FROM public.finance_account_balance " +
                        "WHERE company_id = :companyId " +
                        "AND fiscal_period_id = :fiscalPeriodId " +
                        "AND currency_code = :currencyCode " +
                        "AND branch_id IS NULL",
                params(companyId, fiscalPeriodId).addValue("currencyCode", currencyCode));
    }

    public long countMissingCompanyBalanceProjectionGroups(int companyId, UUID fiscalPeriodId, String currencyCode) {
        return count(
                "SELECT COUNT(*) FROM ( " +
                        "SELECT l.company_id, l.fiscal_period_id, l.account_id, l.currency_code " +
                        "FROM public.finance_journal_line l " +
                        "JOIN public.finance_journal_entry j " +
                        "ON j.company_id = l.company_id AND j.journal_entry_id = l.journal_entry_id " +
                        "WHERE l.company_id = :companyId " +
                        "AND l.fiscal_period_id = :fiscalPeriodId " +
                        "AND l.currency_code = :currencyCode " +
                        "AND j.status IN ('posted', 'reversed') " +
                        "GROUP BY l.company_id, l.fiscal_period_id, l.account_id, l.currency_code " +
                        ") posted_groups " +
                        "LEFT JOIN public.finance_account_balance b " +
                        "ON b.company_id = posted_groups.company_id " +
                        "AND b.fiscal_period_id = posted_groups.fiscal_period_id " +
                        "AND b.account_id = posted_groups.account_id " +
                        "AND b.currency_code = posted_groups.currency_code " +
                        "AND b.branch_id IS NULL " +
                        "WHERE b.account_balance_id IS NULL",
                params(companyId, fiscalPeriodId).addValue("currencyCode", currencyCode));
    }

    public long countUnclosedNominalBalanceGroups(int companyId, UUID fiscalPeriodId, String currencyCode) {
        return count(
                "SELECT COUNT(*) FROM ( " +
                        nominalBalanceGroupSql() +
                        ") nominal_balances " +
                        "WHERE normal_balance_amount <> 0",
                params(companyId, fiscalPeriodId).addValue("currencyCode", currencyCode));
    }

    public long countNonClosableNominalBalanceGroups(int companyId, UUID fiscalPeriodId, String currencyCode) {
        return count(
                "SELECT COUNT(*) FROM ( " +
                        nominalBalanceGroupSql() +
                        ") nominal_balances " +
                        "WHERE normal_balance_amount <> 0 " +
                        "AND (account_status <> 'active' OR is_postable = FALSE)",
                params(companyId, fiscalPeriodId).addValue("currencyCode", currencyCode));
    }

    public ArrayList<NominalClosingBalance> getNominalClosingBalances(int companyId,
                                                                      UUID fiscalPeriodId,
                                                                      String currencyCode) {
        return new ArrayList<>(namedParameterJdbcTemplate.query(
                "SELECT account_id, account_code, account_name, account_type, normal_balance, branch_id, " +
                        "debit_amount, credit_amount, normal_balance_amount " +
                        "FROM ( " +
                        nominalBalanceGroupSql() +
                        ") nominal_balances " +
                        "WHERE normal_balance_amount <> 0 " +
                        "ORDER BY branch_id NULLS FIRST, account_type ASC, account_code ASC",
                params(companyId, fiscalPeriodId).addValue("currencyCode", currencyCode),
                (rs, rowNum) -> mapNominalClosingBalance(rs)));
    }

    public UUID getLatestClosingJournalId(int companyId, UUID fiscalPeriodId, String currencyCode) {
        ArrayList<UUID> journalIds = new ArrayList<>(namedParameterJdbcTemplate.query(
                "SELECT journal_entry_id " +
                        "FROM public.finance_journal_entry " +
                        "WHERE company_id = :companyId " +
                        "AND fiscal_period_id = :fiscalPeriodId " +
                        "AND currency_code = :currencyCode " +
                        "AND journal_type = 'closing' " +
                        "AND source_module = 'system' " +
                        "AND source_type = 'retained_earnings_close' " +
                        "AND status = 'posted' " +
                        "ORDER BY posted_at DESC, journal_number DESC " +
                        "LIMIT 1",
                params(companyId, fiscalPeriodId).addValue("currencyCode", currencyCode),
                (rs, rowNum) -> rs.getObject("journal_entry_id", UUID.class)));
        return journalIds.isEmpty() ? null : journalIds.getFirst();
    }

    public long countClosingJournals(int companyId, UUID fiscalPeriodId, String currencyCode) {
        return count(
                "SELECT COUNT(*) FROM public.finance_journal_entry " +
                        "WHERE company_id = :companyId " +
                        "AND fiscal_period_id = :fiscalPeriodId " +
                        "AND currency_code = :currencyCode " +
                        "AND journal_type = 'closing' " +
                        "AND source_module = 'system' " +
                        "AND source_type = 'retained_earnings_close' " +
                        "AND status = 'posted'",
                params(companyId, fiscalPeriodId).addValue("currencyCode", currencyCode));
    }

    public CloseBalanceTotals getCompanyCloseBalanceTotals(int companyId, UUID fiscalPeriodId, String currencyCode) {
        return namedParameterJdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(closing_debit), 0) AS total_closing_debit, " +
                        "COALESCE(SUM(closing_credit), 0) AS total_closing_credit " +
                        "FROM public.finance_account_balance " +
                        "WHERE company_id = :companyId " +
                        "AND fiscal_period_id = :fiscalPeriodId " +
                        "AND currency_code = :currencyCode " +
                        "AND branch_id IS NULL",
                params(companyId, fiscalPeriodId).addValue("currencyCode", currencyCode),
                (rs, rowNum) -> new CloseBalanceTotals(
                        rs.getBigDecimal("total_closing_debit"),
                        rs.getBigDecimal("total_closing_credit")));
    }

    public UUID createTrialBalanceSnapshot(int companyId,
                                           UUID fiscalPeriodId,
                                           String snapshotType,
                                           boolean includesClosingEntries,
                                           String currencyCode,
                                           BigDecimal totalDebit,
                                           BigDecimal totalCredit,
                                           boolean balanced,
                                           long balanceRowCount,
                                           Integer generatedBy) {
        return namedParameterJdbcTemplate.queryForObject(
                "INSERT INTO public.finance_trial_balance_snapshot " +
                        "(company_id, fiscal_period_id, snapshot_type, includes_closing_entries, currency_code, " +
                        "generated_by, total_debit, total_credit, is_balanced, balance_row_count, created_by) " +
                        "VALUES (:companyId, :fiscalPeriodId, :snapshotType, :includesClosingEntries, :currencyCode, " +
                        ":generatedBy, :totalDebit, :totalCredit, :balanced, :balanceRowCount, :generatedBy) " +
                        "RETURNING trial_balance_snapshot_id",
                params(companyId, fiscalPeriodId)
                        .addValue("snapshotType", snapshotType)
                        .addValue("includesClosingEntries", includesClosingEntries)
                        .addValue("currencyCode", currencyCode)
                        .addValue("generatedBy", generatedBy)
                        .addValue("totalDebit", totalDebit)
                        .addValue("totalCredit", totalCredit)
                        .addValue("balanced", balanced)
                        .addValue("balanceRowCount", balanceRowCount),
                UUID.class);
    }

    public SnapshotEvidence getSnapshotEvidence(int companyId, UUID trialBalanceSnapshotId) {
        return namedParameterJdbcTemplate.queryForObject(
                "SELECT trial_balance_snapshot_id, fiscal_period_id, snapshot_type, includes_closing_entries, " +
                        "currency_code, total_debit, total_credit, is_balanced " +
                        "FROM public.finance_trial_balance_snapshot " +
                        "WHERE company_id = :companyId " +
                        "AND trial_balance_snapshot_id = :trialBalanceSnapshotId",
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("trialBalanceSnapshotId", trialBalanceSnapshotId),
                (rs, rowNum) -> new SnapshotEvidence(
                        rs.getObject("trial_balance_snapshot_id", UUID.class),
                        rs.getObject("fiscal_period_id", UUID.class),
                        rs.getString("snapshot_type"),
                        rs.getBoolean("includes_closing_entries"),
                        rs.getString("currency_code"),
                        rs.getBigDecimal("total_debit"),
                        rs.getBigDecimal("total_credit"),
                        rs.getBoolean("is_balanced")));
    }

    public UUID createCompletedCloseRun(int companyId,
                                        UUID fiscalPeriodId,
                                        UUID trialBalanceSnapshotId,
                                        Integer actorUserId) {
        return namedParameterJdbcTemplate.queryForObject(
                "INSERT INTO public.finance_period_close_run " +
                        "(company_id, fiscal_period_id, status, started_by, started_at, completed_by, completed_at, " +
                        "trial_balance_snapshot_id, created_by, updated_by) " +
                        "VALUES (:companyId, :fiscalPeriodId, 'completed', :actorUserId, NOW(), :actorUserId, NOW(), " +
                        ":trialBalanceSnapshotId, :actorUserId, :actorUserId) " +
                        "RETURNING period_close_run_id",
                params(companyId, fiscalPeriodId)
                        .addValue("trialBalanceSnapshotId", trialBalanceSnapshotId)
                        .addValue("actorUserId", actorUserId),
                UUID.class);
    }

    public UUID createReopenedCloseRun(int companyId,
                                       UUID fiscalPeriodId,
                                       Integer actorUserId) {
        return namedParameterJdbcTemplate.queryForObject(
                "INSERT INTO public.finance_period_close_run " +
                        "(company_id, fiscal_period_id, status, started_by, started_at, completed_by, completed_at, " +
                        "created_by, updated_by) " +
                        "VALUES (:companyId, :fiscalPeriodId, 'reopened', :actorUserId, NOW(), :actorUserId, NOW(), " +
                        ":actorUserId, :actorUserId) " +
                        "RETURNING period_close_run_id",
                params(companyId, fiscalPeriodId)
                        .addValue("actorUserId", actorUserId),
                UUID.class);
    }

    public boolean closeFiscalPeriod(int companyId,
                                     UUID fiscalPeriodId,
                                     int expectedVersion,
                                     Integer actorUserId) {
        int rows = namedParameterJdbcTemplate.update(
                "UPDATE public.finance_fiscal_period " +
                        "SET status = 'hard_closed', " +
                        "locked_at = COALESCE(locked_at, NOW()), " +
                        "locked_by = COALESCE(locked_by, :actorUserId), " +
                        "closed_at = NOW(), " +
                        "closed_by = :actorUserId, " +
                        "version = version + 1, " +
                        "updated_by = :actorUserId " +
                        "WHERE company_id = :companyId " +
                        "AND fiscal_period_id = :fiscalPeriodId " +
                        "AND version = :expectedVersion " +
                        "AND status IN ('open', 'soft_locked')",
                params(companyId, fiscalPeriodId)
                        .addValue("expectedVersion", expectedVersion)
                        .addValue("actorUserId", actorUserId));
        return rows == 1;
    }

    public boolean reopenFiscalPeriod(int companyId,
                                      UUID fiscalPeriodId,
                                      int expectedVersion,
                                      Integer actorUserId) {
        int rows = namedParameterJdbcTemplate.update(
                "UPDATE public.finance_fiscal_period " +
                        "SET status = 'open', " +
                        "locked_at = NULL, " +
                        "locked_by = NULL, " +
                        "closed_at = NULL, " +
                        "closed_by = NULL, " +
                        "version = version + 1, " +
                        "updated_by = :actorUserId " +
                        "WHERE company_id = :companyId " +
                        "AND fiscal_period_id = :fiscalPeriodId " +
                        "AND version = :expectedVersion " +
                        "AND status = 'hard_closed'",
                params(companyId, fiscalPeriodId)
                        .addValue("expectedVersion", expectedVersion)
                        .addValue("actorUserId", actorUserId));
        return rows == 1;
    }

    private long count(String sql, MapSqlParameterSource params) {
        Long value = namedParameterJdbcTemplate.queryForObject(sql, params, Long.class);
        return value == null ? 0 : value;
    }

    private MapSqlParameterSource params(int companyId, UUID fiscalPeriodId) {
        return new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("fiscalPeriodId", fiscalPeriodId);
    }

    private String nominalBalanceGroupSql() {
        return "SELECT l.account_id, a.account_code, a.account_name, a.account_type, a.normal_balance, " +
                "a.status AS account_status, a.is_postable, l.branch_id, " +
                "SUM(l.debit_amount) AS debit_amount, " +
                "SUM(l.credit_amount) AS credit_amount, " +
                "CASE WHEN a.normal_balance = 'credit' " +
                "THEN SUM(l.credit_amount) - SUM(l.debit_amount) " +
                "ELSE SUM(l.debit_amount) - SUM(l.credit_amount) END AS normal_balance_amount " +
                "FROM public.finance_journal_line l " +
                "JOIN public.finance_journal_entry j " +
                "ON j.company_id = l.company_id AND j.journal_entry_id = l.journal_entry_id " +
                "JOIN public.finance_account a " +
                "ON a.company_id = l.company_id AND a.account_id = l.account_id " +
                "WHERE l.company_id = :companyId " +
                "AND l.fiscal_period_id = :fiscalPeriodId " +
                "AND l.currency_code = :currencyCode " +
                "AND j.status IN ('posted', 'reversed') " +
                "AND a.account_type IN ('revenue', 'expense') " +
                "GROUP BY l.account_id, a.account_code, a.account_name, a.account_type, a.normal_balance, " +
                "a.status, a.is_postable, l.branch_id";
    }

    private NominalClosingBalance mapNominalClosingBalance(ResultSet rs) throws SQLException {
        return new NominalClosingBalance(
                rs.getObject("account_id", UUID.class),
                rs.getString("account_code"),
                rs.getString("account_name"),
                rs.getString("account_type"),
                rs.getString("normal_balance"),
                nullableInteger(rs, "branch_id"),
                rs.getBigDecimal("debit_amount"),
                rs.getBigDecimal("credit_amount"),
                rs.getBigDecimal("normal_balance_amount"));
    }

    private Integer nullableInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    public record CloseBalanceTotals(BigDecimal totalClosingDebit, BigDecimal totalClosingCredit) {
    }

    public record SnapshotEvidence(UUID trialBalanceSnapshotId,
                                   UUID fiscalPeriodId,
                                   String snapshotType,
                                   boolean includesClosingEntries,
                                   String currencyCode,
                                   BigDecimal totalDebit,
                                   BigDecimal totalCredit,
                                   boolean balanced) {
    }

    public record NominalClosingBalance(UUID accountId,
                                        String accountCode,
                                        String accountName,
                                        String accountType,
                                        String normalBalance,
                                        Integer branchId,
                                        BigDecimal debitAmount,
                                        BigDecimal creditAmount,
                                        BigDecimal normalBalanceAmount) {
    }
}
