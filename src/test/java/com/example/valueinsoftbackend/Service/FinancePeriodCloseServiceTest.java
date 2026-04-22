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
import com.example.valueinsoftbackend.Model.Finance.FinancePeriodCloseValidationResponse;
import com.example.valueinsoftbackend.Model.Finance.FinancePeriodCloseRunResponse;
import com.example.valueinsoftbackend.Model.Finance.FinanceTrialBalanceSnapshotResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinancePeriodCloseServiceTest {

    private static final int COMPANY_ID = 7;
    private static final UUID FISCAL_PERIOD_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID FISCAL_YEAR_ID = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID REVENUE_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000301");
    private static final UUID EXPENSE_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000302");
    private static final UUID RETAINED_EARNINGS_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000303");
    private static final UUID CLOSING_JOURNAL_ID = UUID.fromString("00000000-0000-0000-0000-000000000401");
    private static final UUID SNAPSHOT_ID = UUID.fromString("00000000-0000-0000-0000-000000000601");
    private static final UUID CLOSE_RUN_ID = UUID.fromString("00000000-0000-0000-0000-000000000701");

    private DbFinanceClose dbFinanceClose;
    private DbFinanceJournal dbFinanceJournal;
    private DbFinanceSetup dbFinanceSetup;
    private AuthorizationService authorizationService;
    private FinanceAuditService financeAuditService;
    private FinancePeriodCloseService service;

    @BeforeEach
    void setUp() {
        dbFinanceClose = Mockito.mock(DbFinanceClose.class);
        dbFinanceJournal = Mockito.mock(DbFinanceJournal.class);
        dbFinanceSetup = Mockito.mock(DbFinanceSetup.class);
        authorizationService = Mockito.mock(AuthorizationService.class);
        financeAuditService = Mockito.mock(FinanceAuditService.class);

        service = new FinancePeriodCloseService(
                dbFinanceClose,
                dbFinanceJournal,
                dbFinanceSetup,
                authorizationService,
                financeAuditService);

        when(dbFinanceSetup.companyExists(COMPANY_ID)).thenReturn(true);
        when(dbFinanceSetup.fiscalPeriodExists(COMPANY_ID, FISCAL_PERIOD_ID)).thenReturn(true);
        when(dbFinanceSetup.getFiscalPeriodById(COMPANY_ID, FISCAL_PERIOD_ID)).thenReturn(openPeriod());
        when(financeAuditService.resolveActorUserId("sam")).thenReturn(11);
        when(financeAuditService.recordEvent(any(), anyInt(), any(), any(), any(), any(), any(), any()))
                .thenReturn("corr-1");
        stubNoCloseBlockers();
    }

    @Test
    void validatePeriodCloseBlocksWhenNominalBalancesAreNotClosed() {
        when(dbFinanceClose.countUnclosedNominalBalanceGroups(COMPANY_ID, FISCAL_PERIOD_ID, "EGP"))
                .thenReturn(2L);

        FinancePeriodCloseValidationResponse response = service.validatePeriodCloseForAuthenticatedUser(
                "sam",
                COMPANY_ID,
                FISCAL_PERIOD_ID,
                "EGP");

        assertEquals(false, response.isCloseAllowed());
        assertTrue(response.getChecks().stream().anyMatch(check ->
                "UNCLOSED_REVENUE_EXPENSE_ACCOUNTS".equals(check.getCode())
                        && "blocked".equals(check.getStatus())
                        && check.getCount() == 2L));
    }

    @Test
    void generateClosingEntriesRequiresRetainedEarningsMapping() {
        when(dbFinanceClose.getNominalClosingBalances(COMPANY_ID, FISCAL_PERIOD_ID, "EGP"))
                .thenReturn(nominalBalances());
        when(dbFinanceSetup.resolveActiveAccountMapping(COMPANY_ID, null, "closing.retained_earnings",
                LocalDate.of(2026, 1, 31))).thenReturn(null);

        ApiException exception = assertThrows(ApiException.class, () ->
                service.generateClosingEntriesForAuthenticatedUser("sam", COMPANY_ID, FISCAL_PERIOD_ID, "EGP"));

        assertEquals("FINANCE_RETAINED_EARNINGS_MAPPING_MISSING", exception.getCode());
    }

    @Test
    void generateClosingEntriesRequiresPostableEquityRetainedEarningsAccount() {
        when(dbFinanceClose.getNominalClosingBalances(COMPANY_ID, FISCAL_PERIOD_ID, "EGP"))
                .thenReturn(nominalBalances());
        when(dbFinanceSetup.resolveActiveAccountMapping(COMPANY_ID, null, "closing.retained_earnings",
                LocalDate.of(2026, 1, 31))).thenReturn(retainedEarningsMapping());
        when(dbFinanceSetup.getAccountById(COMPANY_ID, RETAINED_EARNINGS_ACCOUNT_ID))
                .thenReturn(account(RETAINED_EARNINGS_ACCOUNT_ID, "1100", "asset", "debit", true, "active"));

        ApiException exception = assertThrows(ApiException.class, () ->
                service.generateClosingEntriesForAuthenticatedUser("sam", COMPANY_ID, FISCAL_PERIOD_ID, "EGP"));

        assertEquals("FINANCE_RETAINED_EARNINGS_ACCOUNT_INVALID", exception.getCode());
    }

    @Test
    void generateClosingEntriesCreatesBalancedClosingJournalToRetainedEarnings() {
        when(dbFinanceClose.getNominalClosingBalances(COMPANY_ID, FISCAL_PERIOD_ID, "EGP"))
                .thenReturn(nominalBalances());
        when(dbFinanceSetup.resolveActiveAccountMapping(COMPANY_ID, null, "closing.retained_earnings",
                LocalDate.of(2026, 1, 31))).thenReturn(retainedEarningsMapping());
        when(dbFinanceSetup.getAccountById(COMPANY_ID, RETAINED_EARNINGS_ACCOUNT_ID))
                .thenReturn(account(RETAINED_EARNINGS_ACCOUNT_ID, "3300", "equity", "credit", true, "active"));
        when(dbFinanceJournal.allocateSourceJournalNumber(COMPANY_ID, "system.retained_earnings_close", "CL-"))
                .thenReturn("CL-000001");
        when(dbFinanceJournal.createPostedClosingJournal(any())).thenReturn(CLOSING_JOURNAL_ID);
        when(dbFinanceJournal.getJournalById(COMPANY_ID, CLOSING_JOURNAL_ID)).thenReturn(closingJournal());

        FinanceClosingEntryResponse response = service.generateClosingEntriesForAuthenticatedUser(
                "sam",
                COMPANY_ID,
                FISCAL_PERIOD_ID,
                "EGP");

        ArgumentCaptor<DbFinanceJournal.PostedSourceJournalCommand> commandCaptor =
                ArgumentCaptor.forClass(DbFinanceJournal.PostedSourceJournalCommand.class);
        verify(dbFinanceJournal).createPostedClosingJournal(commandCaptor.capture());

        DbFinanceJournal.PostedSourceJournalCommand command = commandCaptor.getValue();
        assertEquals("closing", command.journalType());
        assertEquals("system", command.sourceModule());
        assertEquals("retained_earnings_close", command.sourceType());
        assertEquals(LocalDate.of(2026, 1, 31), command.postingDate());
        assertEquals(money("100.0000"), command.totalDebit());
        assertEquals(money("100.0000"), command.totalCredit());
        assertEquals(3, command.lines().size());

        DbFinanceJournal.PostedSourceJournalLineCommand revenueLine = command.lines().get(0);
        assertEquals(REVENUE_ACCOUNT_ID, revenueLine.accountId());
        assertEquals(5, revenueLine.branchId());
        assertEquals(money("100.0000"), revenueLine.debitAmount());
        assertEquals(money("0.0000"), revenueLine.creditAmount());

        DbFinanceJournal.PostedSourceJournalLineCommand expenseLine = command.lines().get(1);
        assertEquals(EXPENSE_ACCOUNT_ID, expenseLine.accountId());
        assertEquals(5, expenseLine.branchId());
        assertEquals(money("0.0000"), expenseLine.debitAmount());
        assertEquals(money("40.0000"), expenseLine.creditAmount());

        DbFinanceJournal.PostedSourceJournalLineCommand retainedEarningsLine = command.lines().get(2);
        assertEquals(RETAINED_EARNINGS_ACCOUNT_ID, retainedEarningsLine.accountId());
        assertEquals(5, retainedEarningsLine.branchId());
        assertEquals(money("0.0000"), retainedEarningsLine.debitAmount());
        assertEquals(money("60.0000"), retainedEarningsLine.creditAmount());

        verify(dbFinanceJournal).applyPostedJournalToAccountBalances(COMPANY_ID, CLOSING_JOURNAL_ID, 11);
        assertEquals("generated", response.getStatus());
        assertEquals(CLOSING_JOURNAL_ID, response.getJournalEntryId());
        assertEquals(money("60.0000"), response.getNetIncome());
        assertEquals(2, response.getClosedRevenueExpenseLineCount());
        assertEquals(RETAINED_EARNINGS_ACCOUNT_ID, response.getRetainedEarningsAccountId());
        assertNotNull(response.getCorrelationId());
    }

    @Test
    void generateClosingEntriesReturnsExistingClosingJournalWhenNominalBalancesAreAlreadyClosed() {
        when(dbFinanceClose.getNominalClosingBalances(COMPANY_ID, FISCAL_PERIOD_ID, "EGP"))
                .thenReturn(new ArrayList<>());
        when(dbFinanceClose.getLatestClosingJournalId(COMPANY_ID, FISCAL_PERIOD_ID, "EGP"))
                .thenReturn(CLOSING_JOURNAL_ID);
        when(dbFinanceJournal.getJournalById(COMPANY_ID, CLOSING_JOURNAL_ID)).thenReturn(closingJournal());

        FinanceClosingEntryResponse response = service.generateClosingEntriesForAuthenticatedUser(
                "sam",
                COMPANY_ID,
                FISCAL_PERIOD_ID,
                "EGP");

        assertEquals("already_generated", response.getStatus());
        assertEquals(CLOSING_JOURNAL_ID, response.getJournalEntryId());
        assertEquals(money("100.0000"), response.getTotalDebit());
        assertEquals(money("100.0000"), response.getTotalCredit());
    }

    @Test
    void generateClosingEntriesReturnsNotRequiredWhenNoNominalBalancesAndNoPriorClosingJournal() {
        when(dbFinanceClose.getNominalClosingBalances(COMPANY_ID, FISCAL_PERIOD_ID, "EGP"))
                .thenReturn(new ArrayList<>());
        when(dbFinanceClose.getLatestClosingJournalId(COMPANY_ID, FISCAL_PERIOD_ID, "EGP"))
                .thenReturn(null);

        FinanceClosingEntryResponse response = service.generateClosingEntriesForAuthenticatedUser(
                "sam",
                COMPANY_ID,
                FISCAL_PERIOD_ID,
                "EGP");

        assertEquals("not_required", response.getStatus());
        assertEquals(null, response.getJournalEntryId());
        assertEquals(money("0.0000"), response.getTotalDebit());
        assertEquals(money("0.0000"), response.getTotalCredit());
    }

    @Test
    void finalCloseSnapshotIsBlockedUntilNominalBalancesAreClosed() {
        when(dbFinanceClose.countUnclosedNominalBalanceGroups(COMPANY_ID, FISCAL_PERIOD_ID, "EGP"))
                .thenReturn(1L);

        ApiException exception = assertThrows(ApiException.class, () ->
                service.generateTrialBalanceSnapshotForAuthenticatedUser(
                        "sam",
                        COMPANY_ID,
                        FISCAL_PERIOD_ID,
                        "final_close",
                        true,
                        "EGP"));

        assertEquals("FINANCE_CLOSE_VALIDATION_FAILED", exception.getCode());
    }

    @Test
    void finalCloseSnapshotIsAllowedAfterNominalBalancesAreClosed() {
        when(dbFinanceClose.createTrialBalanceSnapshot(
                eq(COMPANY_ID),
                eq(FISCAL_PERIOD_ID),
                eq("final_close"),
                eq(true),
                eq("EGP"),
                eq(money("140.0000")),
                eq(money("140.0000")),
                eq(true),
                eq(4L),
                eq(11))).thenReturn(SNAPSHOT_ID);

        FinanceTrialBalanceSnapshotResponse response = service.generateTrialBalanceSnapshotForAuthenticatedUser(
                "sam",
                COMPANY_ID,
                FISCAL_PERIOD_ID,
                "final_close",
                true,
                "EGP");

        assertEquals(SNAPSHOT_ID, response.getTrialBalanceSnapshotId());
        assertEquals("final_close", response.getSnapshotType());
        assertEquals(true, response.isBalanced());
        assertEquals(true, response.isIncludesClosingEntries());
    }

    @Test
    void closePeriodCompletesWhenFinalSnapshotEvidenceIsValidAndNominalBalancesAreClosed() {
        when(dbFinanceClose.getSnapshotEvidence(COMPANY_ID, SNAPSHOT_ID))
                .thenReturn(new DbFinanceClose.SnapshotEvidence(
                        SNAPSHOT_ID,
                        FISCAL_PERIOD_ID,
                        "final_close",
                        true,
                        "EGP",
                        money("140.0000"),
                        money("140.0000"),
                        true));
        when(dbFinanceClose.closeFiscalPeriod(COMPANY_ID, FISCAL_PERIOD_ID, 1, 11)).thenReturn(true);
        when(dbFinanceClose.createCompletedCloseRun(COMPANY_ID, FISCAL_PERIOD_ID, SNAPSHOT_ID, 11))
                .thenReturn(CLOSE_RUN_ID);

        FinancePeriodCloseRunResponse response = service.closePeriodForAuthenticatedUser(
                "sam",
                COMPANY_ID,
                FISCAL_PERIOD_ID,
                SNAPSHOT_ID,
                1);

        assertEquals(CLOSE_RUN_ID, response.getPeriodCloseRunId());
        assertEquals(SNAPSHOT_ID, response.getTrialBalanceSnapshotId());
        assertEquals("completed", response.getStatus());
        assertEquals("hard_closed", response.getPeriodStatus());
    }

    @Test
    void reopenPeriodRequiresHardClosedPeriod() {
        ApiException exception = assertThrows(ApiException.class, () ->
                service.reopenPeriodForAuthenticatedUser(
                        "sam",
                        COMPANY_ID,
                        FISCAL_PERIOD_ID,
                        1,
                        "Correction required"));

        assertEquals("FINANCE_PERIOD_NOT_HARD_CLOSED", exception.getCode());
    }

    @Test
    void reopenPeriodMovesHardClosedPeriodBackToOpen() {
        when(dbFinanceSetup.getFiscalPeriodById(COMPANY_ID, FISCAL_PERIOD_ID)).thenReturn(hardClosedPeriod());
        when(dbFinanceClose.reopenFiscalPeriod(COMPANY_ID, FISCAL_PERIOD_ID, 2, 11)).thenReturn(true);
        when(dbFinanceClose.createReopenedCloseRun(COMPANY_ID, FISCAL_PERIOD_ID, 11)).thenReturn(CLOSE_RUN_ID);

        FinancePeriodCloseRunResponse response = service.reopenPeriodForAuthenticatedUser(
                "sam",
                COMPANY_ID,
                FISCAL_PERIOD_ID,
                2,
                "Correction required");

        assertEquals(CLOSE_RUN_ID, response.getPeriodCloseRunId());
        assertEquals("reopened", response.getStatus());
        assertEquals("open", response.getPeriodStatus());
    }

    private void stubNoCloseBlockers() {
        when(dbFinanceClose.countPostedJournals(COMPANY_ID, FISCAL_PERIOD_ID)).thenReturn(2L);
        when(dbFinanceClose.countUnpostedJournals(COMPANY_ID, FISCAL_PERIOD_ID)).thenReturn(0L);
        when(dbFinanceClose.countUnbalancedPostedJournals(COMPANY_ID, FISCAL_PERIOD_ID)).thenReturn(0L);
        when(dbFinanceClose.countOpenPostingRequests(COMPANY_ID, FISCAL_PERIOD_ID)).thenReturn(0L);
        when(dbFinanceClose.countOpenPostingBatches(COMPANY_ID, FISCAL_PERIOD_ID)).thenReturn(0L);
        when(dbFinanceClose.countCompanyBalanceRows(COMPANY_ID, FISCAL_PERIOD_ID, "EGP")).thenReturn(4L);
        when(dbFinanceClose.countMissingCompanyBalanceProjectionGroups(COMPANY_ID, FISCAL_PERIOD_ID, "EGP"))
                .thenReturn(0L);
        when(dbFinanceClose.countUnclosedNominalBalanceGroups(COMPANY_ID, FISCAL_PERIOD_ID, "EGP")).thenReturn(0L);
        when(dbFinanceClose.countNonClosableNominalBalanceGroups(COMPANY_ID, FISCAL_PERIOD_ID, "EGP")).thenReturn(0L);
        when(dbFinanceClose.getCompanyCloseBalanceTotals(COMPANY_ID, FISCAL_PERIOD_ID, "EGP"))
                .thenReturn(new DbFinanceClose.CloseBalanceTotals(money("140.0000"), money("140.0000")));
    }

    private FinanceFiscalPeriodItem openPeriod() {
        return new FinanceFiscalPeriodItem(
                FISCAL_PERIOD_ID,
                COMPANY_ID,
                FISCAL_YEAR_ID,
                1,
                "Jan 2026",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                "open",
                null,
                null,
                null,
                null,
                1,
                Instant.now(),
                null,
                Instant.now(),
                null);
    }

    private FinanceFiscalPeriodItem hardClosedPeriod() {
        FinanceFiscalPeriodItem period = openPeriod();
        period.setStatus("hard_closed");
        period.setVersion(2);
        period.setLockedAt(Instant.now());
        period.setLockedBy(11);
        period.setClosedAt(Instant.now());
        period.setClosedBy(11);
        return period;
    }

    private ArrayList<DbFinanceClose.NominalClosingBalance> nominalBalances() {
        ArrayList<DbFinanceClose.NominalClosingBalance> balances = new ArrayList<>();
        balances.add(new DbFinanceClose.NominalClosingBalance(
                REVENUE_ACCOUNT_ID,
                "4000",
                "Sales Revenue",
                "revenue",
                "credit",
                5,
                money("0.0000"),
                money("100.0000"),
                money("100.0000")));
        balances.add(new DbFinanceClose.NominalClosingBalance(
                EXPENSE_ACCOUNT_ID,
                "5000",
                "COGS",
                "expense",
                "debit",
                5,
                money("40.0000"),
                money("0.0000"),
                money("40.0000")));
        return balances;
    }

    private FinanceAccountMappingItem retainedEarningsMapping() {
        return new FinanceAccountMappingItem(
                UUID.fromString("00000000-0000-0000-0000-000000000501"),
                COMPANY_ID,
                null,
                "closing.retained_earnings",
                RETAINED_EARNINGS_ACCOUNT_ID,
                "3300",
                "Retained Earnings",
                100,
                LocalDate.of(2026, 1, 1),
                null,
                "active",
                1,
                Instant.now(),
                null,
                Instant.now(),
                null);
    }

    private FinanceAccountItem account(UUID accountId,
                                       String accountCode,
                                       String accountType,
                                       String normalBalance,
                                       boolean postable,
                                       String status) {
        return new FinanceAccountItem(
                accountId,
                COMPANY_ID,
                accountCode,
                "Account " + accountCode,
                accountType,
                normalBalance,
                null,
                accountCode,
                0,
                postable,
                false,
                status,
                "EGP",
                false,
                false,
                false,
                false,
                false,
                1,
                Instant.now(),
                null,
                Instant.now(),
                null);
    }

    private FinanceJournalEntryItem closingJournal() {
        return new FinanceJournalEntryItem(
                CLOSING_JOURNAL_ID,
                COMPANY_ID,
                null,
                "CL-000001",
                "closing",
                "system",
                "retained_earnings_close",
                FISCAL_PERIOD_ID + ":EGP:1",
                LocalDate.of(2026, 1, 31),
                FISCAL_PERIOD_ID,
                "Jan 2026",
                "Automated retained earnings closing entry for Jan 2026",
                "posted",
                "EGP",
                BigDecimal.ONE.setScale(8),
                money("100.0000"),
                money("100.0000"),
                true,
                Instant.now(),
                11,
                null,
                null,
                null,
                1,
                Instant.now(),
                null,
                Instant.now(),
                null);
    }

    private BigDecimal money(String value) {
        return new BigDecimal(value).setScale(4);
    }
}
