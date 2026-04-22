package com.example.valueinsoftbackend.DatabaseRequests;

import com.example.valueinsoftbackend.Model.Finance.FinanceJournalEntryItem;
import com.example.valueinsoftbackend.Model.Finance.FinanceJournalLineItem;
import com.example.valueinsoftbackend.Model.Request.Finance.FinanceManualJournalCreateRequest;
import com.example.valueinsoftbackend.Model.Request.Finance.FinanceManualJournalLineRequest;
import com.example.valueinsoftbackend.Model.Request.Finance.FinanceManualJournalUpdateRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.UUID;

@Repository
public class DbFinanceJournal {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public DbFinanceJournal(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public boolean journalExists(int companyId, UUID journalEntryId) {
        Integer count = namedParameterJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.finance_journal_entry " +
                        "WHERE company_id = :companyId AND journal_entry_id = :journalEntryId",
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("journalEntryId", journalEntryId),
                Integer.class);
        return count != null && count > 0;
    }

    public boolean costCenterExists(int companyId, UUID costCenterId) {
        Integer count = namedParameterJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.finance_cost_center " +
                        "WHERE company_id = :companyId AND cost_center_id = :costCenterId",
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("costCenterId", costCenterId),
                Integer.class);
        return count != null && count > 0;
    }

    public boolean taxCodeExists(int companyId, UUID taxCodeId) {
        Integer count = namedParameterJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.finance_tax_code " +
                        "WHERE company_id = :companyId AND tax_code_id = :taxCodeId",
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("taxCodeId", taxCodeId),
                Integer.class);
        return count != null && count > 0;
    }

    public FiscalPeriodPostingInfo getFiscalPeriodPostingInfo(int companyId, UUID fiscalPeriodId) {
        return namedParameterJdbcTemplate.queryForObject(
                "SELECT fiscal_period_id, fiscal_year_id, start_date, end_date, status " +
                        "FROM public.finance_fiscal_period " +
                        "WHERE company_id = :companyId AND fiscal_period_id = :fiscalPeriodId",
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("fiscalPeriodId", fiscalPeriodId),
                (rs, rowNum) -> new FiscalPeriodPostingInfo(
                        uuid(rs, "fiscal_period_id"),
                        uuid(rs, "fiscal_year_id"),
                        localDate(rs, "start_date"),
                        localDate(rs, "end_date"),
                        rs.getString("status")));
    }

    public FinanceJournalEntryItem createManualDraftJournal(FinanceManualJournalCreateRequest request,
            String journalNumber,
            BigDecimal totalDebit,
            BigDecimal totalCredit) {
        UUID journalEntryId = namedParameterJdbcTemplate.queryForObject(
                "INSERT INTO public.finance_journal_entry " +
                        "(company_id, branch_id, journal_number, journal_type, source_module, source_type, source_id, "
                        +
                        "posting_date, fiscal_period_id, description, status, currency_code, exchange_rate, " +
                        "total_debit, total_credit, is_closing_entry) " +
                        "VALUES (:companyId, :branchId, :journalNumber, 'adjustment', 'manual', 'manual_journal', NULL, "
                        +
                        ":postingDate, :fiscalPeriodId, :description, 'draft', :currencyCode, :exchangeRate, " +
                        ":totalDebit, :totalCredit, :closingEntry) " +
                        "RETURNING journal_entry_id",
                new MapSqlParameterSource()
                        .addValue("companyId", request.getCompanyId())
                        .addValue("branchId", request.getBranchId())
                        .addValue("journalNumber", journalNumber)
                        .addValue("postingDate", request.getPostingDate())
                        .addValue("fiscalPeriodId", request.getFiscalPeriodId())
                        .addValue("description", request.getDescription().trim())
                        .addValue("currencyCode", request.getCurrencyCode())
                        .addValue("exchangeRate", request.getExchangeRate())
                        .addValue("totalDebit", totalDebit)
                        .addValue("totalCredit", totalCredit)
                        .addValue("closingEntry", request.isClosingEntry()),
                UUID.class);

        int lineNumber = 1;
        for (FinanceManualJournalLineRequest line : request.getLines()) {
            createManualDraftJournalLine(request, journalEntryId, lineNumber, line);
            lineNumber++;
        }

        return getJournalById(request.getCompanyId(), journalEntryId);
    }

    public FinanceJournalEntryItem updateManualDraftJournal(UUID journalEntryId,
            FinanceManualJournalUpdateRequest request,
            BigDecimal totalDebit,
            BigDecimal totalCredit) {
        int rows = namedParameterJdbcTemplate.update(
                "UPDATE public.finance_journal_entry " +
                        "SET branch_id = :branchId, " +
                        "posting_date = :postingDate, " +
                        "fiscal_period_id = :fiscalPeriodId, " +
                        "description = :description, " +
                        "currency_code = :currencyCode, " +
                        "exchange_rate = :exchangeRate, " +
                        "total_debit = :totalDebit, " +
                        "total_credit = :totalCredit, " +
                        "is_closing_entry = :closingEntry, " +
                        "version = version + 1 " +
                        "WHERE company_id = :companyId " +
                        "AND journal_entry_id = :journalEntryId " +
                        "AND status = 'draft' " +
                        "AND source_module = 'manual' " +
                        "AND version = :version",
                new MapSqlParameterSource()
                        .addValue("companyId", request.getCompanyId())
                        .addValue("journalEntryId", journalEntryId)
                        .addValue("branchId", request.getBranchId())
                        .addValue("postingDate", request.getPostingDate())
                        .addValue("fiscalPeriodId", request.getFiscalPeriodId())
                        .addValue("description", request.getDescription().trim())
                        .addValue("currencyCode", request.getCurrencyCode())
                        .addValue("exchangeRate", request.getExchangeRate())
                        .addValue("totalDebit", totalDebit)
                        .addValue("totalCredit", totalCredit)
                        .addValue("closingEntry", request.isClosingEntry())
                        .addValue("version", request.getVersion()));

        if (rows == 0) {
            return null;
        }

        replaceManualDraftJournalLines(request, journalEntryId);
        return getJournalById(request.getCompanyId(), journalEntryId);
    }

    public int voidManualDraftJournal(int companyId, UUID journalEntryId, int version) {
        return namedParameterJdbcTemplate.update(
                "UPDATE public.finance_journal_entry " +
                        "SET status = 'voided', " +
                        "version = version + 1 " +
                        "WHERE company_id = :companyId " +
                        "AND journal_entry_id = :journalEntryId " +
                        "AND status = 'draft' " +
                        "AND source_module = 'manual' " +
                        "AND version = :version",
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("journalEntryId", journalEntryId)
                        .addValue("version", version));
    }

    public int validateManualDraftJournal(int companyId, UUID journalEntryId, int version) {
        return namedParameterJdbcTemplate.update(
                "UPDATE public.finance_journal_entry " +
                        "SET status = 'validated', " +
                        "version = version + 1 " +
                        "WHERE company_id = :companyId " +
                        "AND journal_entry_id = :journalEntryId " +
                        "AND status = 'draft' " +
                        "AND source_module = 'manual' " +
                        "AND total_debit = total_credit " +
                        "AND total_debit > 0 " +
                        "AND version = :version",
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("journalEntryId", journalEntryId)
                        .addValue("version", version));
    }

    public String allocateManualJournalNumber(int companyId) {
        namedParameterJdbcTemplate.update(
                "INSERT INTO public.finance_journal_sequence " +
                        "(company_id, sequence_key, fiscal_year_id, prefix, next_number, padding) " +
                        "VALUES (:companyId, 'manual.journal', NULL, 'MJ-', 1, 6) " +
                        "ON CONFLICT (company_id, sequence_key) WHERE fiscal_year_id IS NULL DO NOTHING",
                new MapSqlParameterSource("companyId", companyId));

        JournalSequenceValue sequence = namedParameterJdbcTemplate.queryForObject(
                "SELECT journal_sequence_id, prefix, next_number, padding " +
                        "FROM public.finance_journal_sequence " +
                        "WHERE company_id = :companyId " +
                        "AND sequence_key = 'manual.journal' " +
                        "AND fiscal_year_id IS NULL " +
                        "FOR UPDATE",
                new MapSqlParameterSource("companyId", companyId),
                (rs, rowNum) -> new JournalSequenceValue(
                        uuid(rs, "journal_sequence_id"),
                        rs.getString("prefix"),
                        rs.getLong("next_number"),
                        rs.getInt("padding")));

        namedParameterJdbcTemplate.update(
                "UPDATE public.finance_journal_sequence " +
                        "SET next_number = next_number + 1, " +
                        "version = version + 1 " +
                        "WHERE company_id = :companyId " +
                        "AND journal_sequence_id = :journalSequenceId",
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("journalSequenceId", sequence.journalSequenceId()));

        return sequence.prefix() + String.format("%0" + sequence.padding() + "d", sequence.nextNumber());
    }

    public int postValidatedManualJournal(int companyId,
            UUID journalEntryId,
            int version,
            String journalNumber,
            int postedBy) {
        return namedParameterJdbcTemplate.update(
                "UPDATE public.finance_journal_entry " +
                        "SET status = 'posted', " +
                        "journal_number = :journalNumber, " +
                        "posted_at = NOW(), " +
                        "posted_by = :postedBy, " +
                        "version = version + 1 " +
                        "WHERE company_id = :companyId " +
                        "AND journal_entry_id = :journalEntryId " +
                        "AND status = 'validated' " +
                        "AND source_module = 'manual' " +
                        "AND total_debit = total_credit " +
                        "AND total_debit > 0 " +
                        "AND version = :version",
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("journalEntryId", journalEntryId)
                        .addValue("version", version)
                        .addValue("journalNumber", journalNumber)
                        .addValue("postedBy", postedBy));
    }

    public ArrayList<FinanceJournalEntryItem> getJournals(int companyId,
            Integer branchId,
            UUID fiscalPeriodId,
            LocalDate fromDate,
            LocalDate toDate,
            String status,
            String journalType,
            String sourceModule,
            int limit,
            int offset) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("branchId", branchId)
                .addValue("fiscalPeriodId", fiscalPeriodId)
                .addValue("fromDate", fromDate)
                .addValue("toDate", toDate)
                .addValue("status", status)
                .addValue("journalType", journalType)
                .addValue("sourceModule", sourceModule)
                .addValue("limit", limit)
                .addValue("offset", offset);

        StringBuilder sql = new StringBuilder(journalSelectSql())
                .append("WHERE j.company_id = :companyId ");

        if (branchId != null) {
            sql.append("AND j.branch_id = :branchId ");
        }
        if (fiscalPeriodId != null) {
            sql.append("AND j.fiscal_period_id = :fiscalPeriodId ");
        }
        if (fromDate != null) {
            sql.append("AND j.posting_date >= :fromDate ");
        }
        if (toDate != null) {
            sql.append("AND j.posting_date <= :toDate ");
        }
        if (status != null && !status.isBlank()) {
            sql.append("AND j.status = :status ");
        }
        if (journalType != null && !journalType.isBlank()) {
            sql.append("AND j.journal_type = :journalType ");
        }
        if (sourceModule != null && !sourceModule.isBlank()) {
            sql.append("AND j.source_module = :sourceModule ");
        }

        sql.append("ORDER BY j.posting_date DESC, j.created_at DESC, j.journal_number DESC ")
                .append("LIMIT :limit OFFSET :offset");

        return new ArrayList<>(namedParameterJdbcTemplate.query(
                sql.toString(),
                params,
                (rs, rowNum) -> mapJournalEntry(rs)));
    }

    public FinanceJournalEntryItem getJournalById(int companyId, UUID journalEntryId) {
        return namedParameterJdbcTemplate.queryForObject(
                journalSelectSql() +
                        "WHERE j.company_id = :companyId AND j.journal_entry_id = :journalEntryId",
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("journalEntryId", journalEntryId),
                (rs, rowNum) -> mapJournalEntry(rs));
    }

    public ArrayList<FinanceJournalLineItem> getJournalLines(int companyId, UUID journalEntryId) {
        return new ArrayList<>(namedParameterJdbcTemplate.query(
                "SELECT l.journal_line_id, l.company_id, l.journal_entry_id, l.line_number, l.account_id, " +
                        "a.account_code, a.account_name, l.branch_id, l.posting_date, l.fiscal_period_id, " +
                        "l.debit_amount, l.credit_amount, l.currency_code, l.exchange_rate, " +
                        "l.foreign_debit_amount, l.foreign_credit_amount, l.description, l.customer_id, " +
                        "l.supplier_id, l.product_id, l.inventory_movement_id, l.payment_id, l.cost_center_id, " +
                        "cc.cost_center_code, cc.cost_center_name, l.tax_code_id, tc.code AS tax_code, " +
                        "l.source_module, l.source_type, l.source_id, l.created_at, l.created_by " +
                        "FROM public.finance_journal_line l " +
                        "JOIN public.finance_account a ON a.company_id = l.company_id AND a.account_id = l.account_id "
                        +
                        "LEFT JOIN public.finance_cost_center cc ON cc.company_id = l.company_id AND cc.cost_center_id = l.cost_center_id "
                        +
                        "LEFT JOIN public.finance_tax_code tc ON tc.company_id = l.company_id AND tc.tax_code_id = l.tax_code_id "
                        +
                        "WHERE l.company_id = :companyId AND l.journal_entry_id = :journalEntryId " +
                        "ORDER BY l.line_number ASC",
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("journalEntryId", journalEntryId),
                (rs, rowNum) -> mapJournalLine(rs)));
    }

    private void createManualDraftJournalLine(FinanceManualJournalCreateRequest request,
            UUID journalEntryId,
            int lineNumber,
            FinanceManualJournalLineRequest line) {
        Integer lineBranchId = line.getBranchId() == null ? request.getBranchId() : line.getBranchId();
        namedParameterJdbcTemplate.update(
                "INSERT INTO public.finance_journal_line " +
                        "(company_id, journal_entry_id, line_number, account_id, branch_id, posting_date, fiscal_period_id, "
                        +
                        "debit_amount, credit_amount, currency_code, exchange_rate, description, customer_id, supplier_id, "
                        +
                        "product_id, inventory_movement_id, payment_id, cost_center_id, tax_code_id, source_module, source_type, source_id) "
                        +
                        "VALUES (:companyId, :journalEntryId, :lineNumber, :accountId, :branchId, :postingDate, :fiscalPeriodId, "
                        +
                        ":debitAmount, :creditAmount, :currencyCode, :exchangeRate, :description, :customerId, :supplierId, "
                        +
                        ":productId, :inventoryMovementId, :paymentId, :costCenterId, :taxCodeId, 'manual', 'manual_journal', NULL)",
                new MapSqlParameterSource()
                        .addValue("companyId", request.getCompanyId())
                        .addValue("journalEntryId", journalEntryId)
                        .addValue("lineNumber", lineNumber)
                        .addValue("accountId", line.getAccountId())
                        .addValue("branchId", lineBranchId)
                        .addValue("postingDate", request.getPostingDate())
                        .addValue("fiscalPeriodId", request.getFiscalPeriodId())
                        .addValue("debitAmount", line.getDebitAmount())
                        .addValue("creditAmount", line.getCreditAmount())
                        .addValue("currencyCode", request.getCurrencyCode())
                        .addValue("exchangeRate", request.getExchangeRate())
                        .addValue("description", line.getDescription())
                        .addValue("customerId", line.getCustomerId())
                        .addValue("supplierId", line.getSupplierId())
                        .addValue("productId", line.getProductId())
                        .addValue("inventoryMovementId", line.getInventoryMovementId())
                        .addValue("paymentId", line.getPaymentId())
                        .addValue("costCenterId", line.getCostCenterId())
                        .addValue("taxCodeId", line.getTaxCodeId()));
    }

    private void replaceManualDraftJournalLines(FinanceManualJournalUpdateRequest request, UUID journalEntryId) {
        namedParameterJdbcTemplate.update(
                "DELETE FROM public.finance_journal_line " +
                        "WHERE company_id = :companyId AND journal_entry_id = :journalEntryId",
                new MapSqlParameterSource()
                        .addValue("companyId", request.getCompanyId())
                        .addValue("journalEntryId", journalEntryId));

        int lineNumber = 1;
        for (FinanceManualJournalLineRequest line : request.getLines()) {
            createManualDraftJournalLine(request, journalEntryId, lineNumber, line);
            lineNumber++;
        }
    }

    private void createManualDraftJournalLine(FinanceManualJournalUpdateRequest request,
            UUID journalEntryId,
            int lineNumber,
            FinanceManualJournalLineRequest line) {
        Integer lineBranchId = line.getBranchId() == null ? request.getBranchId() : line.getBranchId();
        namedParameterJdbcTemplate.update(
                "INSERT INTO public.finance_journal_line " +
                        "(company_id, journal_entry_id, line_number, account_id, branch_id, posting_date, fiscal_period_id, "
                        +
                        "debit_amount, credit_amount, currency_code, exchange_rate, description, customer_id, supplier_id, "
                        +
                        "product_id, inventory_movement_id, payment_id, cost_center_id, tax_code_id, source_module, source_type, source_id) "
                        +
                        "VALUES (:companyId, :journalEntryId, :lineNumber, :accountId, :branchId, :postingDate, :fiscalPeriodId, "
                        +
                        ":debitAmount, :creditAmount, :currencyCode, :exchangeRate, :description, :customerId, :supplierId, "
                        +
                        ":productId, :inventoryMovementId, :paymentId, :costCenterId, :taxCodeId, 'manual', 'manual_journal', NULL)",
                new MapSqlParameterSource()
                        .addValue("companyId", request.getCompanyId())
                        .addValue("journalEntryId", journalEntryId)
                        .addValue("lineNumber", lineNumber)
                        .addValue("accountId", line.getAccountId())
                        .addValue("branchId", lineBranchId)
                        .addValue("postingDate", request.getPostingDate())
                        .addValue("fiscalPeriodId", request.getFiscalPeriodId())
                        .addValue("debitAmount", line.getDebitAmount())
                        .addValue("creditAmount", line.getCreditAmount())
                        .addValue("currencyCode", request.getCurrencyCode())
                        .addValue("exchangeRate", request.getExchangeRate())
                        .addValue("description", line.getDescription())
                        .addValue("customerId", line.getCustomerId())
                        .addValue("supplierId", line.getSupplierId())
                        .addValue("productId", line.getProductId())
                        .addValue("inventoryMovementId", line.getInventoryMovementId())
                        .addValue("paymentId", line.getPaymentId())
                        .addValue("costCenterId", line.getCostCenterId())
                        .addValue("taxCodeId", line.getTaxCodeId()));
    }

    private String journalSelectSql() {
        return "SELECT j.journal_entry_id, j.company_id, j.branch_id, j.journal_number, j.journal_type, " +
                "j.source_module, j.source_type, j.source_id, j.posting_date, j.fiscal_period_id, " +
                "p.name AS fiscal_period_name, j.description, j.status, j.currency_code, j.exchange_rate, " +
                "j.total_debit, j.total_credit, j.is_closing_entry, j.posted_at, j.posted_by, " +
                "j.reversal_of_journal_id, j.reversed_by_journal_id, j.posting_batch_id, j.version, " +
                "j.created_at, j.created_by, j.updated_at, j.updated_by " +
                "FROM public.finance_journal_entry j " +
                "JOIN public.finance_fiscal_period p ON p.company_id = j.company_id AND p.fiscal_period_id = j.fiscal_period_id ";
    }

    private FinanceJournalEntryItem mapJournalEntry(ResultSet rs) throws SQLException {
        return new FinanceJournalEntryItem(
                uuid(rs, "journal_entry_id"),
                rs.getInt("company_id"),
                nullableInteger(rs, "branch_id"),
                rs.getString("journal_number"),
                rs.getString("journal_type"),
                rs.getString("source_module"),
                rs.getString("source_type"),
                rs.getString("source_id"),
                localDate(rs, "posting_date"),
                uuid(rs, "fiscal_period_id"),
                rs.getString("fiscal_period_name"),
                rs.getString("description"),
                rs.getString("status"),
                rs.getString("currency_code"),
                rs.getBigDecimal("exchange_rate"),
                rs.getBigDecimal("total_debit"),
                rs.getBigDecimal("total_credit"),
                rs.getBoolean("is_closing_entry"),
                instant(rs, "posted_at"),
                nullableInteger(rs, "posted_by"),
                uuid(rs, "reversal_of_journal_id"),
                uuid(rs, "reversed_by_journal_id"),
                uuid(rs, "posting_batch_id"),
                rs.getInt("version"),
                instant(rs, "created_at"),
                nullableInteger(rs, "created_by"),
                instant(rs, "updated_at"),
                nullableInteger(rs, "updated_by"));
    }

    private FinanceJournalLineItem mapJournalLine(ResultSet rs) throws SQLException {
        return new FinanceJournalLineItem(
                uuid(rs, "journal_line_id"),
                rs.getInt("company_id"),
                uuid(rs, "journal_entry_id"),
                rs.getInt("line_number"),
                uuid(rs, "account_id"),
                rs.getString("account_code"),
                rs.getString("account_name"),
                nullableInteger(rs, "branch_id"),
                localDate(rs, "posting_date"),
                uuid(rs, "fiscal_period_id"),
                rs.getBigDecimal("debit_amount"),
                rs.getBigDecimal("credit_amount"),
                rs.getString("currency_code"),
                rs.getBigDecimal("exchange_rate"),
                rs.getBigDecimal("foreign_debit_amount"),
                rs.getBigDecimal("foreign_credit_amount"),
                rs.getString("description"),
                nullableInteger(rs, "customer_id"),
                nullableInteger(rs, "supplier_id"),
                nullableLong(rs, "product_id"),
                nullableLong(rs, "inventory_movement_id"),
                rs.getString("payment_id"),
                uuid(rs, "cost_center_id"),
                rs.getString("cost_center_code"),
                rs.getString("cost_center_name"),
                uuid(rs, "tax_code_id"),
                rs.getString("tax_code"),
                rs.getString("source_module"),
                rs.getString("source_type"),
                rs.getString("source_id"),
                instant(rs, "created_at"),
                nullableInteger(rs, "created_by"));
    }

    private UUID uuid(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, UUID.class);
    }

    private LocalDate localDate(ResultSet rs, String column) throws SQLException {
        Date value = rs.getDate(column);
        return value == null ? null : value.toLocalDate();
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private Integer nullableInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private record JournalSequenceValue(UUID journalSequenceId, String prefix, long nextNumber, int padding) {
    }

    public record FiscalPeriodPostingInfo(UUID fiscalPeriodId,
            UUID fiscalYearId,
            LocalDate startDate,
            LocalDate endDate,
            String status) {
    }
}
