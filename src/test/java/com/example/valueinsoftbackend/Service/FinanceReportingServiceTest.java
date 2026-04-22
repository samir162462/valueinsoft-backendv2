package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbFinanceReporting;
import com.example.valueinsoftbackend.DatabaseRequests.DbFinanceSetup;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Finance.FinanceAccountItem;
import com.example.valueinsoftbackend.Model.Finance.FinanceBalanceSheetResponse;
import com.example.valueinsoftbackend.Model.Finance.FinanceGeneralLedgerLineItem;
import com.example.valueinsoftbackend.Model.Finance.FinanceGeneralLedgerResponse;
import com.example.valueinsoftbackend.Model.Finance.FinanceProfitAndLossResponse;
import com.example.valueinsoftbackend.Model.Finance.FinanceStatementLineItem;
import com.example.valueinsoftbackend.Model.Finance.FinanceTrialBalanceLineItem;
import com.example.valueinsoftbackend.Model.Finance.FinanceTrialBalanceResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinanceReportingServiceTest {

    private static final int COMPANY_ID = 7;
    private static final int BRANCH_ID = 3;
    private static final UUID FISCAL_PERIOD_ID = UUID.fromString("00000000-0000-0000-0000-000000007001");
    private static final UUID CASH_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000007101");
    private static final UUID SALES_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000007102");

    private DbFinanceReporting dbFinanceReporting;
    private DbFinanceSetup dbFinanceSetup;
    private AuthorizationService authorizationService;
    private FinanceAuditService financeAuditService;
    private FinanceReportingService service;

    @BeforeEach
    void setUp() {
        dbFinanceReporting = Mockito.mock(DbFinanceReporting.class);
        dbFinanceSetup = Mockito.mock(DbFinanceSetup.class);
        authorizationService = Mockito.mock(AuthorizationService.class);
        financeAuditService = Mockito.mock(FinanceAuditService.class);
        service = new FinanceReportingService(
                dbFinanceReporting,
                dbFinanceSetup,
                authorizationService,
                financeAuditService);

        when(dbFinanceSetup.companyExists(COMPANY_ID)).thenReturn(true);
        when(dbFinanceSetup.fiscalPeriodExists(COMPANY_ID, FISCAL_PERIOD_ID)).thenReturn(true);
        when(dbFinanceSetup.branchBelongsToCompany(COMPANY_ID, BRANCH_ID)).thenReturn(true);
        when(financeAuditService.recordEvent(any(), anyInt(), any(), any(), any(), any(), any(), any()))
                .thenReturn("corr-7");
    }

    @Test
    void trialBalanceCalculatesTotalsAndBalancedFlag() {
        when(dbFinanceReporting.getTrialBalanceLines(COMPANY_ID, FISCAL_PERIOD_ID, BRANCH_ID, "EGP", false))
                .thenReturn(new ArrayList<>(java.util.List.of(
                        trialBalanceLine(CASH_ACCOUNT_ID, "1000", "asset", "debit",
                                money("0.0000"), money("0.0000"), money("150.0000"), money("0.0000"),
                                money("150.0000"), money("0.0000")),
                        trialBalanceLine(SALES_ACCOUNT_ID, "4000", "revenue", "credit",
                                money("0.0000"), money("0.0000"), money("0.0000"), money("150.0000"),
                                money("0.0000"), money("150.0000")))));

        FinanceTrialBalanceResponse response = service.getTrialBalanceForAuthenticatedUser(
                "sam",
                COMPANY_ID,
                FISCAL_PERIOD_ID,
                BRANCH_ID,
                "EGP",
                false);

        assertEquals(money("150.0000"), response.getTotalPeriodDebit());
        assertEquals(money("150.0000"), response.getTotalPeriodCredit());
        assertEquals(money("150.0000"), response.getTotalClosingDebit());
        assertEquals(money("150.0000"), response.getTotalClosingCredit());
        assertEquals(true, response.isBalanced());

        ArgumentCaptor<Map<String, Object>> auditStateCaptor = ArgumentCaptor.forClass(Map.class);
        verify(financeAuditService).recordEvent(
                eq("sam"),
                eq(COMPANY_ID),
                eq(BRANCH_ID),
                eq("finance.report.trial_balance.generated"),
                eq("finance_report"),
                eq(FISCAL_PERIOD_ID.toString()),
                auditStateCaptor.capture(),
                eq("Trial balance generated"));
        assertEquals("trial_balance", auditStateCaptor.getValue().get("reportType"));
        assertEquals(true, auditStateCaptor.getValue().get("balanced"));
    }

    @Test
    void profitAndLossCalculatesRevenueExpensesAndNetIncome() {
        when(dbFinanceReporting.getStatementLines(
                eq(COMPANY_ID),
                eq(FISCAL_PERIOD_ID),
                eq(null),
                eq("EGP"),
                any(),
                eq(false))).thenReturn(new ArrayList<>(java.util.List.of(
                        statementLine(SALES_ACCOUNT_ID, "4000", "Sales", "revenue", "credit", money("500.0000")),
                        statementLine(UUID.randomUUID(), "5000", "COGS", "expense", "debit", money("300.0000")),
                        statementLine(UUID.randomUUID(), "5100", "Fees", "expense", "debit", money("25.0000")))));

        FinanceProfitAndLossResponse response = service.getProfitAndLossForAuthenticatedUser(
                "sam",
                COMPANY_ID,
                FISCAL_PERIOD_ID,
                null,
                "EGP",
                false);

        assertEquals(money("500.0000"), response.getTotalRevenue());
        assertEquals(money("325.0000"), response.getTotalExpenses());
        assertEquals(money("175.0000"), response.getNetIncome());
        assertEquals(1, response.getRevenueLines().size());
        assertEquals(2, response.getExpenseLines().size());
    }

    @Test
    void balanceSheetIncludesCurrentPeriodNetIncomeInEquityPresentation() {
        when(dbFinanceReporting.getStatementLines(
                eq(COMPANY_ID),
                eq(FISCAL_PERIOD_ID),
                eq(null),
                eq("EGP"),
                any(),
                eq(false))).thenReturn(new ArrayList<>(java.util.List.of(
                        statementLine(UUID.randomUUID(), "1000", "Cash", "asset", "debit", money("1000.0000")),
                        statementLine(UUID.randomUUID(), "2000", "Payable", "liability", "credit", money("400.0000")),
                        statementLine(UUID.randomUUID(), "3000", "Equity", "equity", "credit", money("425.0000")),
                        statementLine(UUID.randomUUID(), "4000", "Sales", "revenue", "credit", money("500.0000")),
                        statementLine(UUID.randomUUID(), "5000", "COGS", "expense", "debit", money("325.0000")))));

        FinanceBalanceSheetResponse response = service.getBalanceSheetForAuthenticatedUser(
                "sam",
                COMPANY_ID,
                FISCAL_PERIOD_ID,
                null,
                "EGP",
                false);

        assertEquals(money("1000.0000"), response.getTotalAssets());
        assertEquals(money("400.0000"), response.getTotalLiabilities());
        assertEquals(money("425.0000"), response.getTotalEquity());
        assertEquals(money("175.0000"), response.getNetIncome());
        assertEquals(money("600.0000"), response.getTotalEquityIncludingNetIncome());
        assertEquals(money("1000.0000"), response.getLiabilitiesAndEquity());
        assertEquals(money("0.0000"), response.getBalanceDifference());
        assertEquals(true, response.isBalanced());
    }

    @Test
    void generalLedgerCalculatesNormalBalancesAndPreservesSourceTraceability() {
        when(dbFinanceSetup.accountExists(COMPANY_ID, SALES_ACCOUNT_ID)).thenReturn(true);
        when(dbFinanceSetup.getAccountById(COMPANY_ID, SALES_ACCOUNT_ID))
                .thenReturn(account(SALES_ACCOUNT_ID, "4000", "Sales", "revenue", "credit"));
        when(dbFinanceReporting.getLedgerBalance(COMPANY_ID, FISCAL_PERIOD_ID, SALES_ACCOUNT_ID, null, "EGP"))
                .thenReturn(new DbFinanceReporting.LedgerBalanceValue(
                        money("0.0000"),
                        money("50.0000"),
                        money("0.0000"),
                        money("150.0000"),
                        money("0.0000"),
                        money("200.0000")));
        when(dbFinanceReporting.getGeneralLedgerLines(
                eq(COMPANY_ID),
                eq(FISCAL_PERIOD_ID),
                eq(SALES_ACCOUNT_ID),
                eq(null),
                eq("EGP"),
                eq(money("50.0000")),
                eq(100),
                eq(0))).thenReturn(new ArrayList<>(java.util.List.of(generalLedgerLine())));

        FinanceGeneralLedgerResponse response = service.getGeneralLedgerForAuthenticatedUser(
                "sam",
                COMPANY_ID,
                FISCAL_PERIOD_ID,
                SALES_ACCOUNT_ID,
                null,
                "EGP",
                null,
                null);

        assertEquals(money("50.0000"), response.getOpeningNormalBalance());
        assertEquals(money("200.0000"), response.getClosingNormalBalance());
        assertEquals(1, response.getLines().size());
        assertEquals("pos", response.getLines().getFirst().getSourceModule());
        assertEquals("sale", response.getLines().getFirst().getSourceType());
        assertEquals("SALE-7001", response.getLines().getFirst().getSourceId());
    }

    @Test
    void generalLedgerRejectsInvalidAccount() {
        when(dbFinanceSetup.accountExists(COMPANY_ID, SALES_ACCOUNT_ID)).thenReturn(false);

        ApiException exception = assertThrows(ApiException.class, () ->
                service.getGeneralLedgerForAuthenticatedUser(
                        "sam",
                        COMPANY_ID,
                        FISCAL_PERIOD_ID,
                        SALES_ACCOUNT_ID,
                        null,
                        "EGP",
                        null,
                        null));

        assertEquals("FINANCE_ACCOUNT_INVALID", exception.getCode());
    }

    @Test
    void reportRejectsInvalidCurrencyCode() {
        ApiException exception = assertThrows(ApiException.class, () ->
                service.getTrialBalanceForAuthenticatedUser(
                        "sam",
                        COMPANY_ID,
                        FISCAL_PERIOD_ID,
                        null,
                        "egp",
                        false));

        assertEquals("FINANCE_CURRENCY_INVALID", exception.getCode());
    }

    private FinanceTrialBalanceLineItem trialBalanceLine(UUID accountId,
                                                         String accountCode,
                                                         String accountType,
                                                         String normalBalance,
                                                         BigDecimal openingDebit,
                                                         BigDecimal openingCredit,
                                                         BigDecimal periodDebit,
                                                         BigDecimal periodCredit,
                                                         BigDecimal closingDebit,
                                                         BigDecimal closingCredit) {
        return new FinanceTrialBalanceLineItem(
                accountId,
                accountCode,
                "Account " + accountCode,
                accountType,
                normalBalance,
                accountCode,
                0,
                BRANCH_ID,
                "EGP",
                openingDebit,
                openingCredit,
                periodDebit,
                periodCredit,
                closingDebit,
                closingCredit,
                "credit".equals(normalBalance)
                        ? closingCredit.subtract(closingDebit)
                        : closingDebit.subtract(closingCredit));
    }

    private FinanceStatementLineItem statementLine(UUID accountId,
                                                   String accountCode,
                                                   String accountName,
                                                   String accountType,
                                                   String normalBalance,
                                                   BigDecimal amount) {
        return new FinanceStatementLineItem(
                accountId,
                accountCode,
                accountName,
                accountType,
                normalBalance,
                accountCode,
                0,
                amount);
    }

    private FinanceGeneralLedgerLineItem generalLedgerLine() {
        return new FinanceGeneralLedgerLineItem(
                UUID.fromString("00000000-0000-0000-0000-000000007201"),
                UUID.fromString("00000000-0000-0000-0000-000000007202"),
                "PS-000001",
                "sales",
                "posted",
                2,
                LocalDate.of(2026, 7, 12),
                FISCAL_PERIOD_ID,
                SALES_ACCOUNT_ID,
                "4000",
                "Sales",
                null,
                money("0.0000"),
                money("150.0000"),
                money("200.0000"),
                "EGP",
                "POS sales revenue",
                "pos",
                "sale",
                "SALE-7001",
                null,
                null,
                null,
                null,
                null);
    }

    private FinanceAccountItem account(UUID accountId,
                                       String accountCode,
                                       String accountName,
                                       String accountType,
                                       String normalBalance) {
        return new FinanceAccountItem(
                accountId,
                COMPANY_ID,
                accountCode,
                accountName,
                accountType,
                normalBalance,
                null,
                accountCode,
                0,
                true,
                false,
                "active",
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

    private BigDecimal money(String value) {
        return new BigDecimal(value).setScale(4);
    }
}
