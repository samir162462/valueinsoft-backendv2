package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbFinanceClose;
import com.example.valueinsoftbackend.DatabaseRequests.DbFinanceJournal;
import com.example.valueinsoftbackend.DatabaseRequests.DbFinanceSetup;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Finance.FinanceAccountItem;
import com.example.valueinsoftbackend.Model.Finance.FinanceAccountMappingItem;
import com.example.valueinsoftbackend.Model.Finance.FinanceClosingEntryResponse;
import com.example.valueinsoftbackend.Model.Finance.FinanceFiscalPeriodItem;
import com.example.valueinsoftbackend.Model.Finance.FinanceJournalEntryItem;
import com.example.valueinsoftbackend.Model.Finance.FinancePeriodCloseValidationCheckItem;
import com.example.valueinsoftbackend.Model.Finance.FinancePeriodCloseValidationResponse;
import com.example.valueinsoftbackend.Model.Finance.FinancePeriodCloseRunResponse;
import com.example.valueinsoftbackend.Model.Finance.FinanceTrialBalanceSnapshotResponse;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class FinancePeriodCloseService {

    private final DbFinanceClose dbFinanceClose;
    private final DbFinanceJournal dbFinanceJournal;
    private final DbFinanceSetup dbFinanceSetup;
    private final AuthorizationService authorizationService;
    private final FinanceAuditService financeAuditService;

    public FinancePeriodCloseService(DbFinanceClose dbFinanceClose,
                                     DbFinanceJournal dbFinanceJournal,
                                     DbFinanceSetup dbFinanceSetup,
                                     AuthorizationService authorizationService,
                                     FinanceAuditService financeAuditService) {
        this.dbFinanceClose = dbFinanceClose;
        this.dbFinanceJournal = dbFinanceJournal;
        this.dbFinanceSetup = dbFinanceSetup;
        this.authorizationService = authorizationService;
        this.financeAuditService = financeAuditService;
    }

    public FinancePeriodCloseValidationResponse validatePeriodCloseForAuthenticatedUser(String authenticatedName,
                                                                                       int companyId,
                                                                                       UUID fiscalPeriodId,
                                                                                       String currencyCode) {
        requireCompany(companyId);
        validateCurrencyCode(currencyCode);
        FinanceFiscalPeriodItem period = requireFiscalPeriod(companyId, fiscalPeriodId);
        authorizeRead(authenticatedName, companyId);

        long postedJournalCount = dbFinanceClose.countPostedJournals(companyId, fiscalPeriodId);
        long unpostedJournalCount = dbFinanceClose.countUnpostedJournals(companyId, fiscalPeriodId);
        long unbalancedPostedJournalCount = dbFinanceClose.countUnbalancedPostedJournals(companyId, fiscalPeriodId);
        long openPostingRequestCount = dbFinanceClose.countOpenPostingRequests(companyId, fiscalPeriodId);
        long openPostingBatchCount = dbFinanceClose.countOpenPostingBatches(companyId, fiscalPeriodId);
        long balanceProjectionRowCount = dbFinanceClose.countCompanyBalanceRows(companyId, fiscalPeriodId, currencyCode);
        long missingProjectionGroupCount = dbFinanceClose.countMissingCompanyBalanceProjectionGroups(
                companyId,
                fiscalPeriodId,
                currencyCode);
        long unclosedNominalBalanceGroupCount = dbFinanceClose.countUnclosedNominalBalanceGroups(
                companyId,
                fiscalPeriodId,
                currencyCode);
        long nonClosableNominalBalanceGroupCount = dbFinanceClose.countNonClosableNominalBalanceGroups(
                companyId,
                fiscalPeriodId,
                currencyCode);
        DbFinanceClose.CloseBalanceTotals totals = dbFinanceClose.getCompanyCloseBalanceTotals(
                companyId,
                fiscalPeriodId,
                currencyCode);

        boolean trialBalanceBalanced = totals.totalClosingDebit().compareTo(totals.totalClosingCredit()) == 0;

        ArrayList<FinancePeriodCloseValidationCheckItem> checks = new ArrayList<>();
        checks.add(check(
                "PERIOD_STATUS",
                isClosableStatus(period.getStatus()) ? 0 : 1,
                "Period must be open or soft-locked before close validation can pass"));
        checks.add(check(
                "UNPOSTED_JOURNALS",
                unpostedJournalCount,
                "Draft or validated journals exist in the period"));
        checks.add(check(
                "UNBALANCED_POSTED_JOURNALS",
                unbalancedPostedJournalCount,
                "Posted journals must be balanced before close"));
        checks.add(check(
                "OPEN_POSTING_REQUESTS",
                openPostingRequestCount,
                "Pending, processing, or failed posting requests are linked to this period"));
        checks.add(check(
                "OPEN_POSTING_BATCHES",
                openPostingBatchCount,
                "Pending, processing, failed, or partially posted batches are linked to this period"));
        checks.add(check(
                "MISSING_BALANCE_PROJECTIONS",
                missingProjectionGroupCount,
                "Posted journal line groups are missing company-level balance projection rows"));
        checks.add(check(
                "TRIAL_BALANCE_BALANCED",
                trialBalanceBalanced ? 0 : 1,
                "Company-level closing debit and credit totals must balance"));
        checks.add(check(
                "NON_CLOSABLE_NOMINAL_ACCOUNTS",
                nonClosableNominalBalanceGroupCount,
                "Revenue or expense balances exist on inactive or non-postable accounts"));
        checks.add(check(
                "UNCLOSED_REVENUE_EXPENSE_ACCOUNTS",
                unclosedNominalBalanceGroupCount,
                "Revenue and expense balances must be closed to retained earnings before final close"));

        boolean closeAllowed = true;
        for (FinancePeriodCloseValidationCheckItem check : checks) {
            if ("blocked".equals(check.getStatus())) {
                closeAllowed = false;
                break;
            }
        }

        FinancePeriodCloseValidationResponse response = new FinancePeriodCloseValidationResponse(
                companyId,
                fiscalPeriodId,
                period.getName(),
                period.getStatus(),
                currencyCode,
                closeAllowed,
                totals.totalClosingDebit(),
                totals.totalClosingCredit(),
                trialBalanceBalanced,
                postedJournalCount,
                balanceProjectionRowCount,
                Instant.now(),
                checks);
        financeAuditService.recordEvent(
                authenticatedName,
                companyId,
                null,
                "finance.period_close.validation.generated",
                "finance_fiscal_period",
                fiscalPeriodId.toString(),
                Map.of(
                        "fiscalPeriodId", fiscalPeriodId.toString(),
                        "fiscalPeriodStatus", period.getStatus(),
                        "currencyCode", currencyCode,
                        "closeAllowed", closeAllowed,
                        "postedJournalCount", postedJournalCount,
                        "balanceProjectionRowCount", balanceProjectionRowCount,
                        "unclosedNominalBalanceGroupCount", unclosedNominalBalanceGroupCount,
                        "trialBalanceBalanced", trialBalanceBalanced,
                        "checkCount", checks.size()),
                "Period close validation generated");
        return response;
    }

    @Transactional
    public FinanceClosingEntryResponse generateClosingEntriesForAuthenticatedUser(String authenticatedName,
                                                                                  int companyId,
                                                                                  UUID fiscalPeriodId,
                                                                                  String currencyCode) {
        requireCompany(companyId);
        validateCurrencyCode(currencyCode);
        authorizeWrite(authenticatedName, companyId);
        FinanceFiscalPeriodItem period = requireFiscalPeriod(companyId, fiscalPeriodId);
        dbFinanceClose.lockFiscalPeriodForCloseWork(companyId, fiscalPeriodId);
        period = requireFiscalPeriod(companyId, fiscalPeriodId);

        if (!isClosableStatus(period.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "FINANCE_PERIOD_NOT_CLOSABLE",
                    "Closing entries can only be generated for open or soft-locked periods");
        }

        assertClosingEntryGenerationAllowed(companyId, fiscalPeriodId, currencyCode);

        ArrayList<DbFinanceClose.NominalClosingBalance> balances = dbFinanceClose.getNominalClosingBalances(
                companyId,
                fiscalPeriodId,
                currencyCode);
        if (balances.isEmpty()) {
            UUID latestClosingJournalId = dbFinanceClose.getLatestClosingJournalId(companyId, fiscalPeriodId, currencyCode);
            if (latestClosingJournalId != null) {
                FinanceJournalEntryItem existing = dbFinanceJournal.getJournalById(companyId, latestClosingJournalId);
                return closingResponse(
                        companyId,
                        fiscalPeriodId,
                        existing,
                        "already_generated",
                        BigDecimal.ZERO.setScale(4),
                        0,
                        null,
                        null,
                        null);
            }
            return new FinanceClosingEntryResponse(
                    companyId,
                    fiscalPeriodId,
                    null,
                    null,
                    "not_required",
                    currencyCode,
                    BigDecimal.ZERO.setScale(4),
                    BigDecimal.ZERO.setScale(4),
                    BigDecimal.ZERO.setScale(4),
                    0,
                    null,
                    null,
                    Instant.now(),
                    null);
        }

        FinanceAccountMappingItem retainedEarningsMapping = dbFinanceSetup.resolveActiveAccountMapping(
                companyId,
                null,
                null,
                "closing.retained_earnings",
                period.getEndDate());
        if (retainedEarningsMapping == null) {
            throw new ApiException(HttpStatus.CONFLICT, "FINANCE_RETAINED_EARNINGS_MAPPING_MISSING",
                    "Active closing.retained_earnings account mapping is required before closing entries can be generated");
        }

        FinanceAccountItem retainedEarningsAccount = dbFinanceSetup.getAccountById(
                companyId,
                retainedEarningsMapping.getAccountId());
        if (!"equity".equals(retainedEarningsAccount.getAccountType())
                || !retainedEarningsAccount.isPostable()
                || !"active".equals(retainedEarningsAccount.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "FINANCE_RETAINED_EARNINGS_ACCOUNT_INVALID",
                    "Retained earnings mapping must resolve to an active postable equity account");
        }

        ClosingBuildResult closingBuild = buildClosingLines(balances, retainedEarningsAccount);
        if (closingBuild.totalDebit().compareTo(BigDecimal.ZERO) <= 0
                || closingBuild.totalDebit().compareTo(closingBuild.totalCredit()) != 0) {
            throw new ApiException(HttpStatus.CONFLICT, "FINANCE_CLOSING_ENTRY_UNBALANCED",
                    "Generated closing entry must balance and be greater than zero");
        }

        Integer actorUserId = financeAuditService.resolveActorUserId(authenticatedName);
        long sequenceNumber = dbFinanceClose.countClosingJournals(companyId, fiscalPeriodId, currencyCode) + 1;
        String sourceId = fiscalPeriodId + ":" + currencyCode + ":" + sequenceNumber;
        String journalNumber = dbFinanceJournal.allocateSourceJournalNumber(
                companyId,
                "system.retained_earnings_close",
                "CL-");
        UUID journalEntryId = dbFinanceJournal.createPostedClosingJournal(
                new DbFinanceJournal.PostedSourceJournalCommand(
                        companyId,
                        null,
                        journalNumber,
                        "closing",
                        "system",
                        "retained_earnings_close",
                        sourceId,
                        period.getEndDate(),
                        fiscalPeriodId,
                        "Automated retained earnings closing entry for " + period.getName(),
                        currencyCode,
                        BigDecimal.ONE.setScale(8),
                        closingBuild.totalDebit(),
                        closingBuild.totalCredit(),
                        actorUserId,
                        closingBuild.lines()));
        dbFinanceJournal.applyPostedJournalToAccountBalances(
                companyId,
                journalEntryId,
                actorUserId == null ? 0 : actorUserId);

        FinanceJournalEntryItem postedJournal = dbFinanceJournal.getJournalById(companyId, journalEntryId);
        String correlationId = financeAuditService.recordEvent(
                authenticatedName,
                companyId,
                null,
                "finance.period_close.closing_entries.generated",
                "finance_journal_entry",
                journalEntryId.toString(),
                Map.of(
                        "fiscalPeriodId", fiscalPeriodId.toString(),
                        "journalNumber", postedJournal.getJournalNumber(),
                        "currencyCode", currencyCode,
                        "totalDebit", postedJournal.getTotalDebit(),
                        "totalCredit", postedJournal.getTotalCredit(),
                        "netIncome", closingBuild.netIncome(),
                        "closedRevenueExpenseLineCount", balances.size(),
                        "retainedEarningsAccountId", retainedEarningsAccount.getAccountId().toString(),
                        "retainedEarningsAccountCode", retainedEarningsAccount.getAccountCode()),
                "Automated closing entry generated to retained earnings");

        return closingResponse(
                companyId,
                fiscalPeriodId,
                postedJournal,
                "generated",
                closingBuild.netIncome(),
                balances.size(),
                retainedEarningsAccount.getAccountId(),
                retainedEarningsAccount.getAccountCode(),
                correlationId);
    }

    @Transactional
    public FinanceTrialBalanceSnapshotResponse generateTrialBalanceSnapshotForAuthenticatedUser(String authenticatedName,
                                                                                                int companyId,
                                                                                                UUID fiscalPeriodId,
                                                                                                String snapshotType,
                                                                                                boolean includesClosingEntries,
                                                                                                String currencyCode) {
        requireCompany(companyId);
        validateCurrencyCode(currencyCode);
        String normalizedSnapshotType = normalizeSnapshotType(snapshotType);
        FinanceFiscalPeriodItem period = requireFiscalPeriod(companyId, fiscalPeriodId);
        authorizeWrite(authenticatedName, companyId);

        DbFinanceClose.CloseBalanceTotals totals = dbFinanceClose.getCompanyCloseBalanceTotals(
                companyId,
                fiscalPeriodId,
                currencyCode);
        boolean balanced = totals.totalClosingDebit().compareTo(totals.totalClosingCredit()) == 0;
        long balanceRowCount = dbFinanceClose.countCompanyBalanceRows(companyId, fiscalPeriodId, currencyCode);

        if ("final_close".equals(normalizedSnapshotType)) {
            assertFinalCloseSnapshotAllowed(
                    companyId,
                    fiscalPeriodId,
                    currencyCode,
                    period.getStatus(),
                    balanced,
                    includesClosingEntries);
        }

        Integer actorUserId = financeAuditService.resolveActorUserId(authenticatedName);
        UUID snapshotId = dbFinanceClose.createTrialBalanceSnapshot(
                companyId,
                fiscalPeriodId,
                normalizedSnapshotType,
                includesClosingEntries,
                currencyCode,
                totals.totalClosingDebit(),
                totals.totalClosingCredit(),
                balanced,
                balanceRowCount,
                actorUserId);
        String correlationId = financeAuditService.recordEvent(
                authenticatedName,
                companyId,
                null,
                "finance.trial_balance_snapshot.generated",
                "finance_trial_balance_snapshot",
                snapshotId.toString(),
                Map.of(
                        "fiscalPeriodId", fiscalPeriodId.toString(),
                        "fiscalPeriodStatus", period.getStatus(),
                        "snapshotType", normalizedSnapshotType,
                        "includesClosingEntries", includesClosingEntries,
                        "currencyCode", currencyCode,
                        "totalDebit", totals.totalClosingDebit(),
                        "totalCredit", totals.totalClosingCredit(),
                        "balanced", balanced,
                        "balanceRowCount", balanceRowCount),
                "Trial balance snapshot generated for period close evidence");

        return new FinanceTrialBalanceSnapshotResponse(
                companyId,
                fiscalPeriodId,
                snapshotId,
                normalizedSnapshotType,
                includesClosingEntries,
                currencyCode,
                totals.totalClosingDebit(),
                totals.totalClosingCredit(),
                balanced,
                balanceRowCount,
                Instant.now(),
                correlationId);
    }

    @Transactional
    public FinancePeriodCloseRunResponse closePeriodForAuthenticatedUser(String authenticatedName,
                                                                         int companyId,
                                                                         UUID fiscalPeriodId,
                                                                         UUID trialBalanceSnapshotId,
                                                                         int expectedVersion) {
        requireCompany(companyId);
        FinanceFiscalPeriodItem period = requireFiscalPeriod(companyId, fiscalPeriodId);
        authorizeWrite(authenticatedName, companyId);

        if (trialBalanceSnapshotId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_CLOSE_SNAPSHOT_REQUIRED",
                    "A final close trial balance snapshot is required to close the period");
        }

        DbFinanceClose.SnapshotEvidence snapshot = requireSnapshotEvidence(companyId, trialBalanceSnapshotId);
        if (!fiscalPeriodId.equals(snapshot.fiscalPeriodId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_CLOSE_SNAPSHOT_PERIOD_INVALID",
                    "Trial balance snapshot does not belong to the fiscal period");
        }
        if (!"final_close".equals(snapshot.snapshotType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_CLOSE_SNAPSHOT_TYPE_INVALID",
                    "Period close requires a final_close trial balance snapshot");
        }

        assertFinalCloseSnapshotAllowed(
                companyId,
                fiscalPeriodId,
                snapshot.currencyCode(),
                period.getStatus(),
                snapshot.balanced(),
                snapshot.includesClosingEntries());

        Integer actorUserId = financeAuditService.resolveActorUserId(authenticatedName);
        boolean closed = dbFinanceClose.closeFiscalPeriod(
                companyId,
                fiscalPeriodId,
                expectedVersion,
                actorUserId);
        if (!closed) {
            throw new ApiException(HttpStatus.CONFLICT, "FINANCE_PERIOD_CLOSE_CONFLICT",
                    "Fiscal period was changed by another request or is no longer closable");
        }

        UUID closeRunId = dbFinanceClose.createCompletedCloseRun(
                companyId,
                fiscalPeriodId,
                trialBalanceSnapshotId,
                actorUserId);
        String correlationId = financeAuditService.recordEvent(
                authenticatedName,
                companyId,
                null,
                "finance.period_close.completed",
                "finance_period_close_run",
                closeRunId.toString(),
                Map.of(
                        "fiscalPeriodId", fiscalPeriodId.toString(),
                        "trialBalanceSnapshotId", trialBalanceSnapshotId.toString(),
                        "currencyCode", snapshot.currencyCode(),
                        "totalDebit", snapshot.totalDebit(),
                        "totalCredit", snapshot.totalCredit(),
                        "periodStatus", "hard_closed"),
                "Fiscal period closed");

        return new FinancePeriodCloseRunResponse(
                companyId,
                fiscalPeriodId,
                closeRunId,
                trialBalanceSnapshotId,
                "completed",
                "hard_closed",
                Instant.now(),
                correlationId);
    }

    @Transactional
    public FinancePeriodCloseRunResponse reopenPeriodForAuthenticatedUser(String authenticatedName,
                                                                          int companyId,
                                                                          UUID fiscalPeriodId,
                                                                          int expectedVersion,
                                                                          String reason) {
        requireCompany(companyId);
        FinanceFiscalPeriodItem period = requireFiscalPeriod(companyId, fiscalPeriodId);
        authorizeWrite(authenticatedName, companyId);
        validateReason(reason, "FINANCE_REOPEN_REASON_REQUIRED");

        if (!"hard_closed".equals(period.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "FINANCE_PERIOD_NOT_HARD_CLOSED",
                    "Only hard-closed fiscal periods can be reopened");
        }

        Integer actorUserId = financeAuditService.resolveActorUserId(authenticatedName);
        boolean reopened = dbFinanceClose.reopenFiscalPeriod(
                companyId,
                fiscalPeriodId,
                expectedVersion,
                actorUserId);
        if (!reopened) {
            throw new ApiException(HttpStatus.CONFLICT, "FINANCE_PERIOD_REOPEN_CONFLICT",
                    "Fiscal period was changed by another request or is no longer hard-closed");
        }

        UUID closeRunId = dbFinanceClose.createReopenedCloseRun(companyId, fiscalPeriodId, actorUserId);
        String correlationId = financeAuditService.recordEvent(
                authenticatedName,
                companyId,
                null,
                "finance.period_close.reopened",
                "finance_period_close_run",
                closeRunId.toString(),
                Map.of(
                        "fiscalPeriodId", fiscalPeriodId.toString(),
                        "previousStatus", period.getStatus(),
                        "periodStatus", "open",
                        "reason", reason.trim()),
                "Fiscal period reopened");

        return new FinancePeriodCloseRunResponse(
                companyId,
                fiscalPeriodId,
                closeRunId,
                null,
                "reopened",
                "open",
                Instant.now(),
                correlationId);
    }

    private FinancePeriodCloseValidationCheckItem check(String code, long count, String message) {
        return new FinancePeriodCloseValidationCheckItem(
                code,
                "blocker",
                count == 0 ? "passed" : "blocked",
                count == 0 ? "Passed" : message,
                count);
    }

    private boolean isClosableStatus(String status) {
        return "open".equals(status) || "soft_locked".equals(status);
    }

    private String normalizeSnapshotType(String snapshotType) {
        String value = snapshotType == null || snapshotType.isBlank()
                ? "pre_close"
                : snapshotType.trim().toLowerCase();
        if (!"pre_close".equals(value) && !"final_close".equals(value) && !"diagnostic".equals(value)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_SNAPSHOT_TYPE_INVALID",
                    "Snapshot type must be pre_close, final_close, or diagnostic");
        }
        return value;
    }

    private DbFinanceClose.SnapshotEvidence requireSnapshotEvidence(int companyId, UUID trialBalanceSnapshotId) {
        try {
            return dbFinanceClose.getSnapshotEvidence(companyId, trialBalanceSnapshotId);
        } catch (EmptyResultDataAccessException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "FINANCE_TRIAL_BALANCE_SNAPSHOT_NOT_FOUND",
                    "Trial balance snapshot does not exist for the company");
        }
    }

    private void assertFinalCloseSnapshotAllowed(int companyId,
                                                 UUID fiscalPeriodId,
                                                 String currencyCode,
                                                 String periodStatus,
                                                 boolean balanced,
                                                 boolean includesClosingEntries) {
        if (!includesClosingEntries) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_FINAL_CLOSE_SNAPSHOT_INVALID",
                    "Final close snapshots must include closing entries");
        }
        if (!isClosableStatus(periodStatus)) {
            throw new ApiException(HttpStatus.CONFLICT, "FINANCE_PERIOD_NOT_CLOSABLE",
                    "Final close snapshot can only be generated for open or soft-locked periods");
        }
        if (!balanced) {
            throw new ApiException(HttpStatus.CONFLICT, "FINANCE_TRIAL_BALANCE_UNBALANCED",
                    "Final close snapshot requires balanced company-level debit and credit totals");
        }
        if (dbFinanceClose.countUnpostedJournals(companyId, fiscalPeriodId) > 0
                || dbFinanceClose.countUnbalancedPostedJournals(companyId, fiscalPeriodId) > 0
                || dbFinanceClose.countOpenPostingRequests(companyId, fiscalPeriodId) > 0
                || dbFinanceClose.countOpenPostingBatches(companyId, fiscalPeriodId) > 0
                || dbFinanceClose.countMissingCompanyBalanceProjectionGroups(companyId, fiscalPeriodId, currencyCode) > 0
                || dbFinanceClose.countNonClosableNominalBalanceGroups(companyId, fiscalPeriodId, currencyCode) > 0
                || dbFinanceClose.countUnclosedNominalBalanceGroups(companyId, fiscalPeriodId, currencyCode) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "FINANCE_CLOSE_VALIDATION_FAILED",
                    "Final close snapshot requires period close validation blockers to be resolved");
        }
    }

    private void assertClosingEntryGenerationAllowed(int companyId, UUID fiscalPeriodId, String currencyCode) {
        if (dbFinanceClose.countUnpostedJournals(companyId, fiscalPeriodId) > 0
                || dbFinanceClose.countUnbalancedPostedJournals(companyId, fiscalPeriodId) > 0
                || dbFinanceClose.countOpenPostingRequests(companyId, fiscalPeriodId) > 0
                || dbFinanceClose.countOpenPostingBatches(companyId, fiscalPeriodId) > 0
                || dbFinanceClose.countMissingCompanyBalanceProjectionGroups(companyId, fiscalPeriodId, currencyCode) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "FINANCE_CLOSE_VALIDATION_FAILED",
                    "Closing entries require unposted journals, open posting work, and projection gaps to be resolved first");
        }
        if (dbFinanceClose.countNonClosableNominalBalanceGroups(companyId, fiscalPeriodId, currencyCode) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "FINANCE_NON_CLOSABLE_NOMINAL_ACCOUNTS",
                    "Revenue and expense balances on inactive or non-postable accounts must be fixed before closing");
        }
    }

    private ClosingBuildResult buildClosingLines(ArrayList<DbFinanceClose.NominalClosingBalance> balances,
                                                 FinanceAccountItem retainedEarningsAccount) {
        BigDecimal totalDebit = BigDecimal.ZERO.setScale(4);
        BigDecimal totalCredit = BigDecimal.ZERO.setScale(4);
        BigDecimal totalRevenue = BigDecimal.ZERO.setScale(4);
        BigDecimal totalExpense = BigDecimal.ZERO.setScale(4);
        ArrayList<DbFinanceJournal.PostedSourceJournalLineCommand> lines = new ArrayList<>();
        LinkedHashMap<Integer, BranchClosingTotals> branchTotals = new LinkedHashMap<>();

        for (DbFinanceClose.NominalClosingBalance balance : balances) {
            BigDecimal amount = balance.normalBalanceAmount().abs();
            BigDecimal debit = BigDecimal.ZERO.setScale(4);
            BigDecimal credit = BigDecimal.ZERO.setScale(4);

            if ("revenue".equals(balance.accountType())) {
                totalRevenue = totalRevenue.add(balance.normalBalanceAmount());
                if (balance.normalBalanceAmount().compareTo(BigDecimal.ZERO) > 0) {
                    debit = amount;
                } else {
                    credit = amount;
                }
            } else {
                totalExpense = totalExpense.add(balance.normalBalanceAmount());
                if (balance.normalBalanceAmount().compareTo(BigDecimal.ZERO) > 0) {
                    credit = amount;
                } else {
                    debit = amount;
                }
            }

            totalDebit = totalDebit.add(debit);
            totalCredit = totalCredit.add(credit);
            addBranchTotals(branchTotals, balance.branchId(), debit, credit);
            lines.add(new DbFinanceJournal.PostedSourceJournalLineCommand(
                    balance.accountId(),
                    balance.branchId(),
                    debit,
                    credit,
                    "Close " + balance.accountCode() + " to retained earnings",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null));
        }

        for (Map.Entry<Integer, BranchClosingTotals> entry : branchTotals.entrySet()) {
            BigDecimal branchDebit = entry.getValue().debit();
            BigDecimal branchCredit = entry.getValue().credit();
            int comparison = branchDebit.compareTo(branchCredit);
            if (comparison == 0) {
                continue;
            }
            if (entry.getKey() == null && retainedEarningsAccount.isRequiresBranch()) {
                throw new ApiException(HttpStatus.CONFLICT, "FINANCE_RETAINED_EARNINGS_BRANCH_REQUIRED",
                        "Retained earnings account requires branch, but company-level closing balance exists");
            }

            BigDecimal retainedDebit = BigDecimal.ZERO.setScale(4);
            BigDecimal retainedCredit = BigDecimal.ZERO.setScale(4);
            if (comparison > 0) {
                retainedCredit = branchDebit.subtract(branchCredit);
            } else {
                retainedDebit = branchCredit.subtract(branchDebit);
            }
            totalDebit = totalDebit.add(retainedDebit);
            totalCredit = totalCredit.add(retainedCredit);
            lines.add(new DbFinanceJournal.PostedSourceJournalLineCommand(
                    retainedEarningsAccount.getAccountId(),
                    entry.getKey(),
                    retainedDebit,
                    retainedCredit,
                    "Transfer period net income to retained earnings",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null));
        }

        return new ClosingBuildResult(
                lines,
                totalDebit,
                totalCredit,
                totalRevenue.subtract(totalExpense));
    }

    private void addBranchTotals(LinkedHashMap<Integer, BranchClosingTotals> branchTotals,
                                 Integer branchId,
                                 BigDecimal debit,
                                 BigDecimal credit) {
        BranchClosingTotals existing = branchTotals.getOrDefault(
                branchId,
                new BranchClosingTotals(BigDecimal.ZERO.setScale(4), BigDecimal.ZERO.setScale(4)));
        branchTotals.put(branchId, new BranchClosingTotals(
                existing.debit().add(debit),
                existing.credit().add(credit)));
    }

    private FinanceClosingEntryResponse closingResponse(int companyId,
                                                        UUID fiscalPeriodId,
                                                        FinanceJournalEntryItem journal,
                                                        String status,
                                                        BigDecimal netIncome,
                                                        int closedLineCount,
                                                        UUID retainedEarningsAccountId,
                                                        String retainedEarningsAccountCode,
                                                        String correlationId) {
        return new FinanceClosingEntryResponse(
                companyId,
                fiscalPeriodId,
                journal.getJournalEntryId(),
                journal.getJournalNumber(),
                status,
                journal.getCurrencyCode(),
                journal.getTotalDebit(),
                journal.getTotalCredit(),
                netIncome,
                closedLineCount,
                retainedEarningsAccountId,
                retainedEarningsAccountCode,
                Instant.now(),
                correlationId);
    }

    private void requireCompany(int companyId) {
        if (!dbFinanceSetup.companyExists(companyId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "FINANCE_COMPANY_NOT_FOUND",
                    "Company does not exist");
        }
    }

    private FinanceFiscalPeriodItem requireFiscalPeriod(int companyId, UUID fiscalPeriodId) {
        if (fiscalPeriodId == null || !dbFinanceSetup.fiscalPeriodExists(companyId, fiscalPeriodId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_FISCAL_PERIOD_INVALID",
                    "Fiscal period does not belong to the company");
        }
        return dbFinanceSetup.getFiscalPeriodById(companyId, fiscalPeriodId);
    }

    private void validateCurrencyCode(String currencyCode) {
        if (currencyCode == null || !currencyCode.matches("^[A-Z]{3}$")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_CURRENCY_INVALID",
                    "Currency code must use three uppercase letters");
        }
    }

    private void validateReason(String reason, String errorCode) {
        if (reason == null || reason.trim().length() < 5) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode,
                    "A reason with at least five characters is required");
        }
    }

    private void authorizeRead(String authenticatedName, int companyId) {
        authorizationService.assertAuthenticatedCapability(
                authenticatedName,
                companyId,
                null,
                "finance.entry.read");
    }

    private void authorizeWrite(String authenticatedName, int companyId) {
        authorizationService.assertAuthenticatedCapability(
                authenticatedName,
                companyId,
                null,
                "finance.entry.edit");
    }

    private record BranchClosingTotals(BigDecimal debit, BigDecimal credit) {
    }

    private record ClosingBuildResult(List<DbFinanceJournal.PostedSourceJournalLineCommand> lines,
                                      BigDecimal totalDebit,
                                      BigDecimal totalCredit,
                                      BigDecimal netIncome) {
    }
}
