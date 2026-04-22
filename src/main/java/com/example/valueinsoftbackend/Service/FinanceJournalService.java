package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbFinanceJournal;
import com.example.valueinsoftbackend.DatabaseRequests.DbFinanceSetup;
import com.example.valueinsoftbackend.DatabaseRequests.DbUsers;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Finance.FinanceAccountItem;
import com.example.valueinsoftbackend.Model.Finance.FinanceJournalDetailResponse;
import com.example.valueinsoftbackend.Model.Finance.FinanceJournalEntryItem;
import com.example.valueinsoftbackend.Model.Finance.FinanceJournalLineItem;
import com.example.valueinsoftbackend.Model.Request.Finance.FinanceManualJournalCreateRequest;
import com.example.valueinsoftbackend.Model.Request.Finance.FinanceManualJournalLineRequest;
import com.example.valueinsoftbackend.Model.Request.Finance.FinanceManualJournalUpdateRequest;
import com.example.valueinsoftbackend.Model.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class FinanceJournalService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4);
    private static final Set<String> JOURNAL_STATUSES = Set.of("draft", "validated", "posted", "reversed", "voided");
    private static final Set<String> JOURNAL_TYPES = Set.of("sales", "sales_return", "purchase", "purchase_return",
            "payment", "inventory", "adjustment", "reversal", "opening_balance", "closing");
    private static final Set<String> SOURCE_MODULES = Set.of("pos", "purchase", "inventory", "payment", "manual",
            "system", "migration");

    private final DbFinanceJournal dbFinanceJournal;
    private final DbFinanceSetup dbFinanceSetup;
    private final DbUsers dbUsers;
    private final AuthorizationService authorizationService;

    public FinanceJournalService(DbFinanceJournal dbFinanceJournal,
                                 DbFinanceSetup dbFinanceSetup,
                                 DbUsers dbUsers,
                                 AuthorizationService authorizationService) {
        this.dbFinanceJournal = dbFinanceJournal;
        this.dbFinanceSetup = dbFinanceSetup;
        this.dbUsers = dbUsers;
        this.authorizationService = authorizationService;
    }

    public ArrayList<FinanceJournalEntryItem> getJournalsForAuthenticatedUser(String authenticatedName,
                                                                              int companyId,
                                                                              Integer branchId,
                                                                              UUID fiscalPeriodId,
                                                                              LocalDate fromDate,
                                                                              LocalDate toDate,
                                                                              String status,
                                                                              String journalType,
                                                                              String sourceModule,
                                                                              Integer limit,
                                                                              Integer offset) {
        requireCompany(companyId);
        requireBranchIfPresent(companyId, branchId);
        authorizeRead(authenticatedName, companyId, branchId);
        validateFilters(companyId, fiscalPeriodId, fromDate, toDate, status, journalType, sourceModule);

        return dbFinanceJournal.getJournals(
                companyId,
                branchId,
                fiscalPeriodId,
                fromDate,
                toDate,
                normalizeOptional(status),
                normalizeOptional(journalType),
                normalizeOptional(sourceModule),
                normalizeLimit(limit),
                normalizeOffset(offset));
    }

    public FinanceJournalDetailResponse getJournalDetailForAuthenticatedUser(String authenticatedName,
                                                                             int companyId,
                                                                             UUID journalEntryId) {
        requireCompany(companyId);
        authorizeRead(authenticatedName, companyId, null);
        requireJournal(companyId, journalEntryId);

        return new FinanceJournalDetailResponse(
                dbFinanceJournal.getJournalById(companyId, journalEntryId),
                dbFinanceJournal.getJournalLines(companyId, journalEntryId));
    }

    public ArrayList<FinanceJournalLineItem> getJournalLinesForAuthenticatedUser(String authenticatedName,
                                                                                 int companyId,
                                                                                 UUID journalEntryId) {
        requireCompany(companyId);
        authorizeRead(authenticatedName, companyId, null);
        requireJournal(companyId, journalEntryId);
        return dbFinanceJournal.getJournalLines(companyId, journalEntryId);
    }

    @Transactional
    public FinanceJournalDetailResponse createManualDraftJournalForAuthenticatedUser(String authenticatedName,
                                                                                    FinanceManualJournalCreateRequest request) {
        requireCompany(request.getCompanyId());
        requireBranchIfPresent(request.getCompanyId(), request.getBranchId());
        authorizeCreate(authenticatedName, request.getCompanyId(), request.getBranchId());
        validateManualDraftJournal(request);

        JournalTotals totals = calculateTotals(request);
        String draftJournalNumber = "DRAFT-" + UUID.randomUUID();
        FinanceJournalEntryItem journal = dbFinanceJournal.createManualDraftJournal(
                request,
                draftJournalNumber,
                totals.totalDebit(),
                totals.totalCredit());

        return new FinanceJournalDetailResponse(
                journal,
                dbFinanceJournal.getJournalLines(request.getCompanyId(), journal.getJournalEntryId()));
    }

    @Transactional
    public FinanceJournalDetailResponse updateManualDraftJournalForAuthenticatedUser(String authenticatedName,
                                                                                    UUID journalEntryId,
                                                                                    FinanceManualJournalUpdateRequest request) {
        requireCompany(request.getCompanyId());
        requireBranchIfPresent(request.getCompanyId(), request.getBranchId());
        authorizeEdit(authenticatedName, request.getCompanyId(), request.getBranchId());
        requireEditableManualDraft(request.getCompanyId(), journalEntryId);
        validateManualDraftJournal(request);

        JournalTotals totals = calculateTotals(request);
        FinanceJournalEntryItem journal = dbFinanceJournal.updateManualDraftJournal(
                journalEntryId,
                request,
                totals.totalDebit(),
                totals.totalCredit());

        if (journal == null) {
            throw new ApiException(HttpStatus.CONFLICT, "FINANCE_VERSION_CONFLICT",
                    "Manual draft journal was updated by another request or is no longer editable");
        }

        return new FinanceJournalDetailResponse(
                journal,
                dbFinanceJournal.getJournalLines(request.getCompanyId(), journalEntryId));
    }

    @Transactional
    public FinanceJournalDetailResponse voidManualDraftJournalForAuthenticatedUser(String authenticatedName,
                                                                                  int companyId,
                                                                                  UUID journalEntryId,
                                                                                  int version) {
        requireCompany(companyId);
        authorizeEdit(authenticatedName, companyId, null);
        requireEditableManualDraft(companyId, journalEntryId);

        int rows = dbFinanceJournal.voidManualDraftJournal(companyId, journalEntryId, version);
        if (rows == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "FINANCE_VERSION_CONFLICT",
                    "Manual draft journal was updated by another request or is no longer editable");
        }

        return new FinanceJournalDetailResponse(
                dbFinanceJournal.getJournalById(companyId, journalEntryId),
                dbFinanceJournal.getJournalLines(companyId, journalEntryId));
    }

    @Transactional
    public FinanceJournalDetailResponse validateManualDraftJournalForAuthenticatedUser(String authenticatedName,
                                                                                      int companyId,
                                                                                      UUID journalEntryId,
                                                                                      int version) {
        requireCompany(companyId);
        authorizeEdit(authenticatedName, companyId, null);
        FinanceJournalEntryItem journal = requireEditableManualDraft(companyId, journalEntryId);
        validateManualDraftPeriod(companyId, journal.getFiscalPeriodId(), journal.getPostingDate());
        validatePersistedDraftLines(journal);

        int rows = dbFinanceJournal.validateManualDraftJournal(companyId, journalEntryId, version);
        if (rows == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "FINANCE_VERSION_CONFLICT",
                    "Manual draft journal was updated by another request or is no longer validatable");
        }

        return new FinanceJournalDetailResponse(
                dbFinanceJournal.getJournalById(companyId, journalEntryId),
                dbFinanceJournal.getJournalLines(companyId, journalEntryId));
    }

    @Transactional
    public FinanceJournalDetailResponse postValidatedManualJournalForAuthenticatedUser(String authenticatedName,
                                                                                      int companyId,
                                                                                      UUID journalEntryId,
                                                                                      int version) {
        requireCompany(companyId);
        authorizeEdit(authenticatedName, companyId, null);
        User actor = requireAuthenticatedUser(authenticatedName);
        FinanceJournalEntryItem journal = requireValidatedManualJournal(companyId, journalEntryId);
        validateManualDraftPeriod(companyId, journal.getFiscalPeriodId(), journal.getPostingDate());
        validatePersistedDraftLines(journal);

        String journalNumber = dbFinanceJournal.allocateManualJournalNumber(companyId);
        int rows = dbFinanceJournal.postValidatedManualJournal(
                companyId,
                journalEntryId,
                version,
                journalNumber,
                actor.getUserId());

        if (rows == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "FINANCE_VERSION_CONFLICT",
                    "Manual journal was updated by another request or is no longer postable");
        }

        return new FinanceJournalDetailResponse(
                dbFinanceJournal.getJournalById(companyId, journalEntryId),
                dbFinanceJournal.getJournalLines(companyId, journalEntryId));
    }

    private void validateFilters(int companyId,
                                 UUID fiscalPeriodId,
                                 LocalDate fromDate,
                                 LocalDate toDate,
                                 String status,
                                 String journalType,
                                 String sourceModule) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_DATE_RANGE_INVALID",
                    "From date must be before or equal to to date");
        }

        if (fiscalPeriodId != null && !dbFinanceSetup.fiscalPeriodExists(companyId, fiscalPeriodId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_FISCAL_PERIOD_INVALID",
                    "Fiscal period does not belong to the company");
        }

        validateOptionalValue(status, JOURNAL_STATUSES, "FINANCE_JOURNAL_STATUS_INVALID");
        validateOptionalValue(journalType, JOURNAL_TYPES, "FINANCE_JOURNAL_TYPE_INVALID");
        validateOptionalValue(sourceModule, SOURCE_MODULES, "FINANCE_SOURCE_MODULE_INVALID");
    }

    private void validateManualDraftJournal(FinanceManualJournalCreateRequest request) {
        if (request.getLines() == null || request.getLines().size() < 2) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_JOURNAL_LINES_INVALID",
                    "Manual journals require at least two lines");
        }

        if (request.getDescription() == null || request.getDescription().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_JOURNAL_DESCRIPTION_REQUIRED",
                    "Manual journal description is required");
        }

        if (request.getCurrencyCode() == null || !request.getCurrencyCode().matches("^[A-Z]{3}$")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_CURRENCY_INVALID",
                    "Currency code must use three uppercase letters");
        }

        if (request.getExchangeRate() == null || request.getExchangeRate().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_EXCHANGE_RATE_INVALID",
                    "Exchange rate must be greater than zero");
        }

        if (!dbFinanceSetup.fiscalPeriodExists(request.getCompanyId(), request.getFiscalPeriodId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_FISCAL_PERIOD_INVALID",
                    "Fiscal period does not belong to the company");
        }

        DbFinanceJournal.FiscalPeriodPostingInfo period = dbFinanceJournal.getFiscalPeriodPostingInfo(
                request.getCompanyId(),
                request.getFiscalPeriodId());

        if (request.getPostingDate() == null ||
                request.getPostingDate().isBefore(period.startDate()) ||
                request.getPostingDate().isAfter(period.endDate())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_POSTING_DATE_OUTSIDE_PERIOD",
                    "Posting date must be inside the selected fiscal period");
        }

        if (!Set.of("open", "soft_locked").contains(period.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "FINANCE_PERIOD_NOT_OPEN",
                    "Manual draft journals can only target open or soft-locked periods");
        }

        JournalTotals totals = calculateTotals(request);
        if (totals.totalDebit().compareTo(BigDecimal.ZERO) <= 0 || totals.totalDebit().compareTo(totals.totalCredit()) != 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_JOURNAL_UNBALANCED",
                    "Manual journal debit and credit totals must balance and be greater than zero");
        }

        for (FinanceManualJournalLineRequest line : request.getLines()) {
            validateManualJournalLine(request, line);
        }
    }

    private void validateManualDraftJournal(FinanceManualJournalUpdateRequest request) {
        if (request.getLines() == null || request.getLines().size() < 2) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_JOURNAL_LINES_INVALID",
                    "Manual journals require at least two lines");
        }

        if (request.getDescription() == null || request.getDescription().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_JOURNAL_DESCRIPTION_REQUIRED",
                    "Manual journal description is required");
        }

        if (request.getCurrencyCode() == null || !request.getCurrencyCode().matches("^[A-Z]{3}$")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_CURRENCY_INVALID",
                    "Currency code must use three uppercase letters");
        }

        if (request.getExchangeRate() == null || request.getExchangeRate().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_EXCHANGE_RATE_INVALID",
                    "Exchange rate must be greater than zero");
        }

        validateManualDraftPeriod(request.getCompanyId(), request.getFiscalPeriodId(), request.getPostingDate());

        JournalTotals totals = calculateTotals(request);
        if (totals.totalDebit().compareTo(BigDecimal.ZERO) <= 0 || totals.totalDebit().compareTo(totals.totalCredit()) != 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_JOURNAL_UNBALANCED",
                    "Manual journal debit and credit totals must balance and be greater than zero");
        }

        for (FinanceManualJournalLineRequest line : request.getLines()) {
            validateManualJournalLine(request.getCompanyId(), request.getBranchId(), line);
        }
    }

    private void validateManualDraftPeriod(int companyId, UUID fiscalPeriodId, LocalDate postingDate) {
        if (!dbFinanceSetup.fiscalPeriodExists(companyId, fiscalPeriodId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_FISCAL_PERIOD_INVALID",
                    "Fiscal period does not belong to the company");
        }

        DbFinanceJournal.FiscalPeriodPostingInfo period = dbFinanceJournal.getFiscalPeriodPostingInfo(
                companyId,
                fiscalPeriodId);

        if (postingDate == null ||
                postingDate.isBefore(period.startDate()) ||
                postingDate.isAfter(period.endDate())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_POSTING_DATE_OUTSIDE_PERIOD",
                    "Posting date must be inside the selected fiscal period");
        }

        if (!Set.of("open", "soft_locked").contains(period.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "FINANCE_PERIOD_NOT_OPEN",
                    "Manual draft journals can only target open or soft-locked periods");
        }
    }

    private void validateManualJournalLine(FinanceManualJournalCreateRequest request,
                                           FinanceManualJournalLineRequest line) {
        if (line.getAccountId() == null || !dbFinanceSetup.accountExists(request.getCompanyId(), line.getAccountId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_JOURNAL_ACCOUNT_INVALID",
                    "Journal line account does not belong to the company");
        }

        FinanceAccountItem account = dbFinanceSetup.getAccountById(request.getCompanyId(), line.getAccountId());
        if (!account.isPostable() || !"active".equals(account.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_JOURNAL_ACCOUNT_NOT_POSTABLE",
                    "Journal lines must use active postable accounts");
        }

        BigDecimal debit = amountOrZero(line.getDebitAmount());
        BigDecimal credit = amountOrZero(line.getCreditAmount());
        if (!((debit.compareTo(BigDecimal.ZERO) > 0 && credit.compareTo(BigDecimal.ZERO) == 0) ||
                (credit.compareTo(BigDecimal.ZERO) > 0 && debit.compareTo(BigDecimal.ZERO) == 0))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_JOURNAL_LINE_AMOUNT_INVALID",
                    "Each journal line must have either debit or credit, but not both");
        }

        Integer lineBranchId = line.getBranchId() == null ? request.getBranchId() : line.getBranchId();
        if (account.isRequiresBranch() && lineBranchId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_JOURNAL_BRANCH_REQUIRED",
                    "This account requires a branch on the journal line");
        }

        requireBranchIfPresent(request.getCompanyId(), lineBranchId);

        if (line.getCostCenterId() != null && !dbFinanceJournal.costCenterExists(request.getCompanyId(), line.getCostCenterId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_JOURNAL_COST_CENTER_INVALID",
                    "Cost center does not belong to the company");
        }

        if (line.getTaxCodeId() != null && !dbFinanceJournal.taxCodeExists(request.getCompanyId(), line.getTaxCodeId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_JOURNAL_TAX_CODE_INVALID",
                    "Tax code does not belong to the company");
        }
    }

    private void validateManualJournalLine(int companyId,
                                           Integer headerBranchId,
                                           FinanceManualJournalLineRequest line) {
        if (line.getAccountId() == null || !dbFinanceSetup.accountExists(companyId, line.getAccountId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_JOURNAL_ACCOUNT_INVALID",
                    "Journal line account does not belong to the company");
        }

        FinanceAccountItem account = dbFinanceSetup.getAccountById(companyId, line.getAccountId());
        if (!account.isPostable() || !"active".equals(account.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_JOURNAL_ACCOUNT_NOT_POSTABLE",
                    "Journal lines must use active postable accounts");
        }

        BigDecimal debit = amountOrZero(line.getDebitAmount());
        BigDecimal credit = amountOrZero(line.getCreditAmount());
        if (!((debit.compareTo(BigDecimal.ZERO) > 0 && credit.compareTo(BigDecimal.ZERO) == 0) ||
                (credit.compareTo(BigDecimal.ZERO) > 0 && debit.compareTo(BigDecimal.ZERO) == 0))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_JOURNAL_LINE_AMOUNT_INVALID",
                    "Each journal line must have either debit or credit, but not both");
        }

        Integer lineBranchId = line.getBranchId() == null ? headerBranchId : line.getBranchId();
        if (account.isRequiresBranch() && lineBranchId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_JOURNAL_BRANCH_REQUIRED",
                    "This account requires a branch on the journal line");
        }

        requireBranchIfPresent(companyId, lineBranchId);

        if (line.getCostCenterId() != null && !dbFinanceJournal.costCenterExists(companyId, line.getCostCenterId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_JOURNAL_COST_CENTER_INVALID",
                    "Cost center does not belong to the company");
        }

        if (line.getTaxCodeId() != null && !dbFinanceJournal.taxCodeExists(companyId, line.getTaxCodeId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_JOURNAL_TAX_CODE_INVALID",
                    "Tax code does not belong to the company");
        }
    }

    private JournalTotals calculateTotals(FinanceManualJournalCreateRequest request) {
        BigDecimal totalDebit = ZERO;
        BigDecimal totalCredit = ZERO;

        for (FinanceManualJournalLineRequest line : request.getLines()) {
            totalDebit = totalDebit.add(amountOrZero(line.getDebitAmount()));
            totalCredit = totalCredit.add(amountOrZero(line.getCreditAmount()));
        }

        return new JournalTotals(totalDebit, totalCredit);
    }

    private JournalTotals calculateTotals(FinanceManualJournalUpdateRequest request) {
        BigDecimal totalDebit = ZERO;
        BigDecimal totalCredit = ZERO;

        for (FinanceManualJournalLineRequest line : request.getLines()) {
            totalDebit = totalDebit.add(amountOrZero(line.getDebitAmount()));
            totalCredit = totalCredit.add(amountOrZero(line.getCreditAmount()));
        }

        return new JournalTotals(totalDebit, totalCredit);
    }

    private BigDecimal amountOrZero(BigDecimal amount) {
        return amount == null ? ZERO : amount;
    }

    private void validateOptionalValue(String value, Set<String> allowedValues, String errorCode) {
        String normalized = normalizeOptional(value);
        if (normalized != null && !allowedValues.contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode, "Invalid finance journal filter");
        }
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_PAGE_LIMIT_INVALID",
                    "Limit must be between 1 and 200");
        }
        return limit;
    }

    private int normalizeOffset(Integer offset) {
        if (offset == null) {
            return 0;
        }
        if (offset < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_PAGE_OFFSET_INVALID",
                    "Offset must be zero or greater");
        }
        return offset;
    }

    private void requireCompany(int companyId) {
        if (!dbFinanceSetup.companyExists(companyId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "FINANCE_COMPANY_NOT_FOUND",
                    "Company does not exist");
        }
    }

    private void requireBranchIfPresent(int companyId, Integer branchId) {
        if (branchId == null) {
            return;
        }

        if (!dbFinanceSetup.branchBelongsToCompany(companyId, branchId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_BRANCH_SCOPE_INVALID",
                    "Branch does not belong to the requested company");
        }
    }

    private void requireJournal(int companyId, UUID journalEntryId) {
        if (!dbFinanceJournal.journalExists(companyId, journalEntryId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "FINANCE_JOURNAL_NOT_FOUND",
                    "Journal does not exist");
        }
    }

    private FinanceJournalEntryItem requireEditableManualDraft(int companyId, UUID journalEntryId) {
        requireJournal(companyId, journalEntryId);
        FinanceJournalEntryItem journal = dbFinanceJournal.getJournalById(companyId, journalEntryId);
        if (!"manual".equals(journal.getSourceModule()) || !"draft".equals(journal.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "FINANCE_JOURNAL_NOT_EDITABLE",
                    "Only manual draft journals can be edited or voided");
        }
        return journal;
    }

    private FinanceJournalEntryItem requireValidatedManualJournal(int companyId, UUID journalEntryId) {
        requireJournal(companyId, journalEntryId);
        FinanceJournalEntryItem journal = dbFinanceJournal.getJournalById(companyId, journalEntryId);
        if (!"manual".equals(journal.getSourceModule()) || !"validated".equals(journal.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "FINANCE_JOURNAL_NOT_POSTABLE",
                    "Only validated manual journals can be posted by this workflow");
        }
        if (journal.getPostedAt() != null || journal.getPostedBy() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "FINANCE_JOURNAL_ALREADY_POSTED",
                    "Journal already has posting metadata");
        }
        return journal;
    }

    private void validatePersistedDraftLines(FinanceJournalEntryItem journal) {
        ArrayList<FinanceJournalLineItem> lines = dbFinanceJournal.getJournalLines(
                journal.getCompanyId(),
                journal.getJournalEntryId());

        if (lines.size() < 2) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_JOURNAL_LINES_INVALID",
                    "Manual journals require at least two lines");
        }

        BigDecimal totalDebit = ZERO;
        BigDecimal totalCredit = ZERO;
        for (FinanceJournalLineItem line : lines) {
            totalDebit = totalDebit.add(amountOrZero(line.getDebitAmount()));
            totalCredit = totalCredit.add(amountOrZero(line.getCreditAmount()));

            FinanceManualJournalLineRequest lineRequest = new FinanceManualJournalLineRequest();
            lineRequest.setAccountId(line.getAccountId());
            lineRequest.setBranchId(line.getBranchId());
            lineRequest.setDebitAmount(line.getDebitAmount());
            lineRequest.setCreditAmount(line.getCreditAmount());
            lineRequest.setCostCenterId(line.getCostCenterId());
            lineRequest.setTaxCodeId(line.getTaxCodeId());
            validateManualJournalLine(journal.getCompanyId(), journal.getBranchId(), lineRequest);
        }

        if (totalDebit.compareTo(BigDecimal.ZERO) <= 0 || totalDebit.compareTo(totalCredit) != 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_JOURNAL_UNBALANCED",
                    "Manual journal debit and credit totals must balance and be greater than zero");
        }

        if (journal.getTotalDebit().compareTo(totalDebit) != 0 || journal.getTotalCredit().compareTo(totalCredit) != 0) {
            throw new ApiException(HttpStatus.CONFLICT, "FINANCE_JOURNAL_TOTAL_MISMATCH",
                    "Journal header totals do not match persisted journal lines");
        }
    }

    private void authorizeRead(String authenticatedName, int companyId, Integer branchId) {
        authorizationService.assertAuthenticatedCapability(
                authenticatedName,
                companyId,
                branchId,
                "finance.entry.read");
    }

    private void authorizeCreate(String authenticatedName, int companyId, Integer branchId) {
        authorizationService.assertAuthenticatedCapability(
                authenticatedName,
                companyId,
                branchId,
                "finance.entry.create");
    }

    private void authorizeEdit(String authenticatedName, int companyId, Integer branchId) {
        authorizationService.assertAuthenticatedCapability(
                authenticatedName,
                companyId,
                branchId,
                "finance.entry.edit");
    }

    private User requireAuthenticatedUser(String authenticatedName) {
        User user = dbUsers.getUser(extractBaseUserName(authenticatedName));
        if (user == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found");
        }
        return user;
    }

    private String extractBaseUserName(String value) {
        if (value != null && value.contains(" : ")) {
            return value.split(" : ")[0];
        }
        return value == null ? "" : value.trim();
    }

    private record JournalTotals(BigDecimal totalDebit, BigDecimal totalCredit) {
    }
}
