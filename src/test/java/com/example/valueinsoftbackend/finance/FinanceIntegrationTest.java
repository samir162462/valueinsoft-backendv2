package com.example.valueinsoftbackend.finance;

import com.example.valueinsoftbackend.AbstractIntegrationTest;
import com.example.valueinsoftbackend.Model.Finance.FinanceAccountBalanceRebuildResponse;
import com.example.valueinsoftbackend.Model.Finance.FinanceAccountItem;
import com.example.valueinsoftbackend.Model.Finance.FinanceJournalDetailResponse;
import com.example.valueinsoftbackend.Model.Finance.FinanceJournalEntryItem;
import com.example.valueinsoftbackend.Model.Finance.FinanceJournalLineItem;
import com.example.valueinsoftbackend.Model.Finance.FinancePeriodCloseValidationCheckItem;
import com.example.valueinsoftbackend.Model.Finance.FinancePeriodCloseValidationResponse;
import com.example.valueinsoftbackend.Model.Finance.FinancePostingRequestProcessResponse;
import com.example.valueinsoftbackend.Model.Finance.FinanceReconciliationRunItem;
import com.example.valueinsoftbackend.Model.Finance.FinanceSetupOverviewResponse;
import com.example.valueinsoftbackend.Model.Finance.FinanceTrialBalanceLineItem;
import com.example.valueinsoftbackend.Model.Finance.FinanceTrialBalanceResponse;
import com.example.valueinsoftbackend.Model.Request.Finance.FinanceAccountCreateRequest;
import com.example.valueinsoftbackend.Model.Request.Finance.FinanceManualJournalCreateRequest;
import com.example.valueinsoftbackend.Model.Request.Finance.FinanceReconciliationRunCreateRequest;
import com.example.valueinsoftbackend.Service.finance.FinanceJournalService;
import com.example.valueinsoftbackend.Service.finance.FinancePeriodCloseService;
import com.example.valueinsoftbackend.Service.finance.FinancePostingRequestService;
import com.example.valueinsoftbackend.Service.finance.FinanceProjectionService;
import com.example.valueinsoftbackend.Service.finance.FinanceReconciliationService;
import com.example.valueinsoftbackend.Service.finance.FinanceReportingService;
import com.example.valueinsoftbackend.Service.finance.FinanceSetupService;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FinanceIntegrationTest extends AbstractIntegrationTest {

    private static final String AUTHENTICATED_USER = "finance_user:Owner";
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final UUID ACCOUNT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OFFSET_ACCOUNT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID FISCAL_PERIOD_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID JOURNAL_ENTRY_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID POSTING_REQUEST_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID RECONCILIATION_RUN_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");

    @MockitoBean
    private FinanceSetupService financeSetupService;

    @MockitoBean
    private FinanceJournalService financeJournalService;

    @MockitoBean
    private FinancePostingRequestService financePostingRequestService;

    @MockitoBean
    private FinanceReportingService financeReportingService;

    @MockitoBean
    private FinancePeriodCloseService financePeriodCloseService;

    @MockitoBean
    private FinanceProjectionService financeProjectionService;

    @MockitoBean
    private FinanceReconciliationService financeReconciliationService;

    @Test
    void shouldRequireAuthenticationForFinanceEndpoints() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/finance/setup/overview")
                        .param("companyId", "1074"))
                .andExpect(status().isUnauthorized());

        verify(financeSetupService, never()).getOverviewForAuthenticatedUser(any(), anyInt());
    }

    @Test
    @WithMockUser(username = AUTHENTICATED_USER, roles = {"USER"})
    void shouldReturnFinanceSetupOverview() throws Exception {
        when(financeSetupService.getOverviewForAuthenticatedUser(eq(AUTHENTICATED_USER), eq(1074)))
                .thenReturn(new FinanceSetupOverviewResponse(
                        1074,
                        1,
                        12,
                        48,
                        8,
                        2,
                        true,
                        true,
                        true,
                        true,
                        true,
                        NOW
                ));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/finance/setup/overview")
                        .param("companyId", "1074"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyId").value(1074))
                .andExpect(jsonPath("$.accountCount").value(48))
                .andExpect(jsonPath("$.setupReadyForPosting").value(true));

        verify(financeSetupService).getOverviewForAuthenticatedUser(AUTHENTICATED_USER, 1074);
    }

    @Test
    @WithMockUser(username = AUTHENTICATED_USER, roles = {"USER"})
    void shouldCreateFinanceAccountFromJson() throws Exception {
        when(financeSetupService.createAccountForAuthenticatedUser(
                eq(AUTHENTICATED_USER),
                any(FinanceAccountCreateRequest.class)
        )).thenReturn(account());

        String payload = """
                {
                  "companyId": 1074,
                  "accountCode": "1100",
                  "accountName": "Cash on hand",
                  "accountType": "asset",
                  "normalBalance": "debit",
                  "postable": true,
                  "system": false,
                  "status": "active",
                  "currencyCode": "EGP",
                  "requiresBranch": true
                }
                """;

        mockMvc.perform(MockMvcRequestBuilders.post("/api/finance/setup/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(ACCOUNT_ID.toString()))
                .andExpect(jsonPath("$.accountCode").value("1100"))
                .andExpect(jsonPath("$.requiresBranch").value(true));

        verify(financeSetupService).createAccountForAuthenticatedUser(eq(AUTHENTICATED_USER), any(FinanceAccountCreateRequest.class));
    }

    @Test
    @WithMockUser(username = AUTHENTICATED_USER, roles = {"USER"})
    void shouldRejectInvalidFinanceAccountPayloadBeforeServiceCall() throws Exception {
        String payload = """
                {
                  "companyId": 0,
                  "accountCode": "",
                  "accountName": "",
                  "accountType": "",
                  "normalBalance": "",
                  "status": "",
                  "currencyCode": "egp"
                }
                """;

        mockMvc.perform(MockMvcRequestBuilders.post("/api/finance/setup/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verify(financeSetupService, never()).createAccountForAuthenticatedUser(any(), any(FinanceAccountCreateRequest.class));
    }

    @Test
    @WithMockUser(username = AUTHENTICATED_USER, roles = {"USER"})
    void shouldCreateManualDraftJournalFromJson() throws Exception {
        when(financeJournalService.createManualDraftJournalForAuthenticatedUser(
                eq(AUTHENTICATED_USER),
                any(FinanceManualJournalCreateRequest.class)
        )).thenReturn(journalDetail("draft"));

        String payload = """
                {
                  "companyId": 1074,
                  "branchId": 1095,
                  "postingDate": "2026-01-15",
                  "fiscalPeriodId": "33333333-3333-3333-3333-333333333333",
                  "description": "Opening cash journal",
                  "currencyCode": "EGP",
                  "exchangeRate": 1,
                  "closingEntry": false,
                  "lines": [
                    {
                      "accountId": "11111111-1111-1111-1111-111111111111",
                      "branchId": 1095,
                      "debitAmount": 100.00,
                      "creditAmount": 0.00,
                      "description": "Cash"
                    },
                    {
                      "accountId": "22222222-2222-2222-2222-222222222222",
                      "branchId": 1095,
                      "debitAmount": 0.00,
                      "creditAmount": 100.00,
                      "description": "Offset"
                    }
                  ]
                }
                """;

        mockMvc.perform(MockMvcRequestBuilders.post("/api/finance/journals/manual-drafts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.journal.journalEntryId").value(JOURNAL_ENTRY_ID.toString()))
                .andExpect(jsonPath("$.journal.status").value("draft"))
                .andExpect(jsonPath("$.lines[0].debitAmount").value(100.0))
                .andExpect(jsonPath("$.lines[1].creditAmount").value(100.0));

        verify(financeJournalService).createManualDraftJournalForAuthenticatedUser(
                eq(AUTHENTICATED_USER),
                any(FinanceManualJournalCreateRequest.class)
        );
    }

    @Test
    @WithMockUser(username = AUTHENTICATED_USER, roles = {"USER"})
    void shouldProcessNextPostingRequest() throws Exception {
        when(financePostingRequestService.processNextPostingRequestForAuthenticatedUser(
                eq(AUTHENTICATED_USER),
                eq(1074),
                eq("pos")
        )).thenReturn(new FinancePostingRequestProcessResponse(
                1074,
                POSTING_REQUEST_ID,
                "pos",
                "order",
                "9001",
                "posted",
                true,
                JOURNAL_ENTRY_ID,
                1,
                "Posted",
                "corr-001"
        ));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/finance/posting-requests/process-next")
                        .param("companyId", "1074")
                        .param("sourceModule", "pos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(true))
                .andExpect(jsonPath("$.postingRequestId").value(POSTING_REQUEST_ID.toString()))
                .andExpect(jsonPath("$.journalEntryId").value(JOURNAL_ENTRY_ID.toString()));

        verify(financePostingRequestService).processNextPostingRequestForAuthenticatedUser(AUTHENTICATED_USER, 1074, "pos");
    }

    @Test
    @WithMockUser(username = AUTHENTICATED_USER, roles = {"USER"})
    void shouldReturnTrialBalanceReport() throws Exception {
        when(financeReportingService.getTrialBalanceForAuthenticatedUser(
                eq(AUTHENTICATED_USER),
                eq(1074),
                eq(FISCAL_PERIOD_ID),
                eq(1095),
                eq("EGP"),
                eq(true)
        )).thenReturn(trialBalance());

        mockMvc.perform(MockMvcRequestBuilders.get("/api/finance/reports/trial-balance")
                        .param("companyId", "1074")
                        .param("fiscalPeriodId", FISCAL_PERIOD_ID.toString())
                        .param("branchId", "1095")
                        .param("currencyCode", "EGP")
                        .param("includeZeroBalances", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanced").value(true))
                .andExpect(jsonPath("$.totalPeriodDebit").value(100.0))
                .andExpect(jsonPath("$.lines[0].accountCode").value("1100"));

        verify(financeReportingService).getTrialBalanceForAuthenticatedUser(
                AUTHENTICATED_USER,
                1074,
                FISCAL_PERIOD_ID,
                1095,
                "EGP",
                true
        );
    }

    @Test
    @WithMockUser(username = AUTHENTICATED_USER, roles = {"USER"})
    void shouldValidatePeriodClose() throws Exception {
        ArrayList<FinancePeriodCloseValidationCheckItem> checks = new ArrayList<>();
        checks.add(new FinancePeriodCloseValidationCheckItem(
                "TRIAL_BALANCE",
                "error",
                "passed",
                "Trial balance is balanced",
                0
        ));
        when(financePeriodCloseService.validatePeriodCloseForAuthenticatedUser(
                eq(AUTHENTICATED_USER),
                eq(1074),
                eq(FISCAL_PERIOD_ID),
                eq("EGP")
        )).thenReturn(new FinancePeriodCloseValidationResponse(
                1074,
                FISCAL_PERIOD_ID,
                "Jan 2026",
                "open",
                "EGP",
                true,
                new BigDecimal("100.00"),
                new BigDecimal("100.00"),
                true,
                4,
                2,
                NOW,
                checks
        ));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/finance/period-close/{fiscalPeriodId}/validation", FISCAL_PERIOD_ID)
                        .param("companyId", "1074")
                        .param("currencyCode", "EGP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closeAllowed").value(true))
                .andExpect(jsonPath("$.checks[0].code").value("TRIAL_BALANCE"))
                .andExpect(jsonPath("$.checks[0].status").value("passed"));

        verify(financePeriodCloseService).validatePeriodCloseForAuthenticatedUser(
                AUTHENTICATED_USER,
                1074,
                FISCAL_PERIOD_ID,
                "EGP"
        );
    }

    @Test
    @WithMockUser(username = AUTHENTICATED_USER, roles = {"USER"})
    void shouldRebuildAccountBalanceProjection() throws Exception {
        when(financeProjectionService.rebuildAccountBalancesForAuthenticatedUser(
                eq(AUTHENTICATED_USER),
                eq(1074),
                eq(FISCAL_PERIOD_ID),
                eq("EGP")
        )).thenReturn(new FinanceAccountBalanceRebuildResponse(
                1074,
                FISCAL_PERIOD_ID,
                "EGP",
                3,
                2,
                1,
                3,
                new BigDecimal("100.00"),
                new BigDecimal("100.00"),
                true,
                NOW
        ));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/finance/projections/account-balances/rebuild")
                        .param("companyId", "1074")
                        .param("fiscalPeriodId", FISCAL_PERIOD_ID.toString())
                        .param("currencyCode", "EGP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanced").value(true))
                .andExpect(jsonPath("$.totalProjectionRowCount").value(3));

        verify(financeProjectionService).rebuildAccountBalancesForAuthenticatedUser(
                AUTHENTICATED_USER,
                1074,
                FISCAL_PERIOD_ID,
                "EGP"
        );
    }

    @Test
    @WithMockUser(username = AUTHENTICATED_USER, roles = {"USER"})
    void shouldCreateReconciliationRun() throws Exception {
        when(financeReconciliationService.createRunForAuthenticatedUser(
                eq(AUTHENTICATED_USER),
                any(FinanceReconciliationRunCreateRequest.class)
        )).thenReturn(new FinanceReconciliationRunItem(
                RECONCILIATION_RUN_ID,
                1074,
                1095,
                "cash",
                LocalDate.parse("2026-01-01"),
                LocalDate.parse("2026-01-31"),
                "open",
                new BigDecimal("0.00"),
                501,
                NOW,
                null,
                NOW,
                501,
                NOW,
                501
        ));

        String payload = """
                {
                  "companyId": 1074,
                  "branchId": 1095,
                  "reconciliationType": "cash",
                  "periodStart": "2026-01-01",
                  "periodEnd": "2026-01-31"
                }
                """;

        mockMvc.perform(MockMvcRequestBuilders.post("/api/finance/reconciliation-runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reconciliationRunId").value(RECONCILIATION_RUN_ID.toString()))
                .andExpect(jsonPath("$.status").value("open"))
                .andExpect(jsonPath("$.reconciliationType").value("cash"));

        verify(financeReconciliationService).createRunForAuthenticatedUser(
                eq(AUTHENTICATED_USER),
                any(FinanceReconciliationRunCreateRequest.class)
        );
    }

    private FinanceAccountItem account() {
        FinanceAccountItem account = new FinanceAccountItem();
        account.setAccountId(ACCOUNT_ID);
        account.setCompanyId(1074);
        account.setAccountCode("1100");
        account.setAccountName("Cash on hand");
        account.setAccountType("asset");
        account.setNormalBalance("debit");
        account.setAccountPath("Assets > Cash");
        account.setAccountLevel(2);
        account.setPostable(true);
        account.setSystem(false);
        account.setStatus("active");
        account.setCurrencyCode("EGP");
        account.setRequiresBranch(true);
        account.setVersion(1);
        account.setCreatedAt(NOW);
        account.setCreatedBy(501);
        account.setUpdatedAt(NOW);
        account.setUpdatedBy(501);
        return account;
    }

    private FinanceJournalDetailResponse journalDetail(String status) {
        FinanceJournalEntryItem journal = new FinanceJournalEntryItem();
        journal.setJournalEntryId(JOURNAL_ENTRY_ID);
        journal.setCompanyId(1074);
        journal.setBranchId(1095);
        journal.setJournalNumber("JV-2026-0001");
        journal.setJournalType("manual");
        journal.setSourceModule("finance");
        journal.setSourceType("manual_journal");
        journal.setPostingDate(LocalDate.parse("2026-01-15"));
        journal.setFiscalPeriodId(FISCAL_PERIOD_ID);
        journal.setFiscalPeriodName("Jan 2026");
        journal.setDescription("Opening cash journal");
        journal.setStatus(status);
        journal.setCurrencyCode("EGP");
        journal.setExchangeRate(BigDecimal.ONE);
        journal.setTotalDebit(new BigDecimal("100.00"));
        journal.setTotalCredit(new BigDecimal("100.00"));
        journal.setVersion(1);
        journal.setCreatedAt(NOW);
        journal.setCreatedBy(501);
        journal.setUpdatedAt(NOW);
        journal.setUpdatedBy(501);

        ArrayList<FinanceJournalLineItem> lines = new ArrayList<>();
        lines.add(journalLine(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), 1, ACCOUNT_ID, "1100", "Cash on hand", "100.00", "0.00"));
        lines.add(journalLine(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"), 2, OFFSET_ACCOUNT_ID, "3000", "Opening balance", "0.00", "100.00"));
        return new FinanceJournalDetailResponse(journal, lines);
    }

    private FinanceJournalLineItem journalLine(UUID journalLineId,
                                               int lineNumber,
                                               UUID accountId,
                                               String accountCode,
                                               String accountName,
                                               String debit,
                                               String credit) {
        FinanceJournalLineItem line = new FinanceJournalLineItem();
        line.setJournalLineId(journalLineId);
        line.setCompanyId(1074);
        line.setJournalEntryId(JOURNAL_ENTRY_ID);
        line.setLineNumber(lineNumber);
        line.setAccountId(accountId);
        line.setAccountCode(accountCode);
        line.setAccountName(accountName);
        line.setBranchId(1095);
        line.setPostingDate(LocalDate.parse("2026-01-15"));
        line.setFiscalPeriodId(FISCAL_PERIOD_ID);
        line.setDebitAmount(new BigDecimal(debit));
        line.setCreditAmount(new BigDecimal(credit));
        line.setCurrencyCode("EGP");
        line.setExchangeRate(BigDecimal.ONE);
        line.setForeignDebitAmount(new BigDecimal(debit));
        line.setForeignCreditAmount(new BigDecimal(credit));
        line.setSourceModule("finance");
        line.setSourceType("manual_journal");
        line.setSourceId(JOURNAL_ENTRY_ID.toString());
        line.setCreatedAt(NOW);
        line.setCreatedBy(501);
        return line;
    }

    private FinanceTrialBalanceResponse trialBalance() {
        ArrayList<FinanceTrialBalanceLineItem> lines = new ArrayList<>();
        lines.add(new FinanceTrialBalanceLineItem(
                ACCOUNT_ID,
                "1100",
                "Cash on hand",
                "asset",
                "debit",
                "Assets > Cash",
                2,
                1095,
                "EGP",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("100.00"),
                BigDecimal.ZERO,
                new BigDecimal("100.00"),
                BigDecimal.ZERO,
                new BigDecimal("100.00")
        ));
        return new FinanceTrialBalanceResponse(
                1074,
                FISCAL_PERIOD_ID,
                1095,
                "EGP",
                true,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("100.00"),
                new BigDecimal("100.00"),
                new BigDecimal("100.00"),
                new BigDecimal("100.00"),
                true,
                NOW,
                lines
        );
    }
}
