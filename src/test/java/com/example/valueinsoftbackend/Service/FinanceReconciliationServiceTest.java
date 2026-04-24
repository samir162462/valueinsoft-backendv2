package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbFinanceReconciliation;
import com.example.valueinsoftbackend.DatabaseRequests.DbFinanceSetup;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Finance.FinanceReconciliationItemItem;
import com.example.valueinsoftbackend.Model.Finance.FinanceReconciliationRunItem;
import com.example.valueinsoftbackend.Model.Finance.FinanceReconciliationSourceImportResponse;
import com.example.valueinsoftbackend.Model.Finance.FinanceReconciliationSourceItem;
import com.example.valueinsoftbackend.Model.Request.Finance.FinanceReconciliationItemResolutionRequest;
import com.example.valueinsoftbackend.Model.Request.Finance.FinanceReconciliationRunCreateRequest;
import com.example.valueinsoftbackend.Model.Request.Finance.FinanceReconciliationSourceImportItemRequest;
import com.example.valueinsoftbackend.Model.Request.Finance.FinanceReconciliationSourceImportRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinanceReconciliationServiceTest {

    private static final int COMPANY_ID = 7;
    private static final int BRANCH_ID = 3;
    private static final UUID RUN_ID = UUID.fromString("00000000-0000-0000-0000-000000006001");
    private static final UUID ITEM_ID = UUID.fromString("00000000-0000-0000-0000-000000006002");
    private static final UUID OTHER_RUN_ID = UUID.fromString("00000000-0000-0000-0000-000000006003");
    private static final UUID SOURCE_ITEM_ID = UUID.fromString("00000000-0000-0000-0000-000000006004");
    private static final UUID LEDGER_LINE_ID = UUID.fromString("00000000-0000-0000-0000-000000006005");

    private DbFinanceReconciliation dbFinanceReconciliation;
    private DbFinanceSetup dbFinanceSetup;
    private AuthorizationService authorizationService;
    private FinanceAuditService financeAuditService;
    private FinanceOperationalPostingService financeOperationalPostingService;
    private StorageService storageService;
    private FinanceReconciliationService service;

    @BeforeEach
    void setUp() {
        dbFinanceReconciliation = Mockito.mock(DbFinanceReconciliation.class);
        dbFinanceSetup = Mockito.mock(DbFinanceSetup.class);
        authorizationService = Mockito.mock(AuthorizationService.class);
        financeAuditService = Mockito.mock(FinanceAuditService.class);
        financeOperationalPostingService = Mockito.mock(FinanceOperationalPostingService.class);
        storageService = Mockito.mock(StorageService.class);
        service = new FinanceReconciliationService(
                dbFinanceReconciliation,
                dbFinanceSetup,
                authorizationService,
                financeAuditService,
                financeOperationalPostingService,
                storageService,
                new ObjectMapper());

        when(dbFinanceSetup.companyExists(COMPANY_ID)).thenReturn(true);
        when(dbFinanceSetup.branchBelongsToCompany(COMPANY_ID, BRANCH_ID)).thenReturn(true);
        when(financeAuditService.resolveActorUserId("sam")).thenReturn(17);
        when(financeAuditService.recordEvent(any(), anyInt(), any(), any(), any(), any(), any(), any()))
                .thenReturn("corr-6");
    }

    @Test
    void createRunExecutesExactMatchingPipelineWithTypeSpecificMappingKeys() {
        FinanceReconciliationRunCreateRequest request = runCreateRequest("card_settlement");
        when(dbFinanceReconciliation.createRun(request, 17)).thenReturn(run("running", "card_settlement"));
        when(dbFinanceReconciliation.completeRun(COMPANY_ID, RUN_ID, 17))
                .thenReturn(run("completed_with_exceptions", "card_settlement"));

        FinanceReconciliationRunItem response = service.createRunForAuthenticatedUser("sam", request);

        ArgumentCaptor<List<String>> mappingKeysCaptor = ArgumentCaptor.forClass(List.class);
        verify(dbFinanceReconciliation).createMatchedSourceItems(
                eq(COMPANY_ID),
                eq(RUN_ID),
                eq(BRANCH_ID),
                eq("card_settlement"),
                eq(LocalDate.of(2026, 4, 1)),
                eq(LocalDate.of(2026, 4, 20)),
                mappingKeysCaptor.capture(),
                eq(17));
        assertEquals(List.of("pos.card", "payment.card_clearing", "payment.fee_expense"),
                mappingKeysCaptor.getValue());
        verify(dbFinanceReconciliation).createUnmatchedSourceItems(
                COMPANY_ID,
                RUN_ID,
                BRANCH_ID,
                "card_settlement",
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 20),
                17);
        verify(dbFinanceReconciliation).refreshSourceItemStatusesForRun(COMPANY_ID, RUN_ID, 17);
        verify(dbFinanceReconciliation).createUnmatchedLedgerItems(
                eq(COMPANY_ID),
                eq(RUN_ID),
                eq(BRANCH_ID),
                eq(LocalDate.of(2026, 4, 1)),
                eq(LocalDate.of(2026, 4, 20)),
                eq(List.of("pos.card", "payment.card_clearing", "payment.fee_expense")),
                eq(17));
        assertEquals("completed_with_exceptions", response.getStatus());
    }

    @Test
    void createRunRejectsInvalidPeriod() {
        ApiException exception = assertThrows(ApiException.class, () ->
                service.createRunForAuthenticatedUser(
                        "sam",
                        new FinanceReconciliationRunCreateRequest(
                                COMPANY_ID,
                                BRANCH_ID,
                                "bank",
                                LocalDate.of(2026, 4, 20),
                                LocalDate.of(2026, 4, 1))));

        assertEquals("FINANCE_RECONCILIATION_PERIOD_INVALID", exception.getCode());
    }

    @Test
    void importSourceItemsNormalizesIdentifiersAmountsAndRawPayload() {
        FinanceReconciliationSourceImportRequest request = sourceImportRequest();
        when(dbFinanceReconciliation.importSourceItems(
                eq(COMPANY_ID),
                eq(BRANCH_ID),
                eq("card_settlement"),
                eq("stripe"),
                eq(request.getItems()),
                any(),
                eq(17))).thenReturn(new ArrayList<>(List.of(sourceItem())));

        FinanceReconciliationSourceImportResponse response = service.importSourceItemsForAuthenticatedUser(
                "sam",
                request);

        ArgumentCaptor<List<String>> rawPayloadCaptor = ArgumentCaptor.forClass(List.class);
        verify(dbFinanceReconciliation).importSourceItems(
                eq(COMPANY_ID),
                eq(BRANCH_ID),
                eq("card_settlement"),
                eq("stripe"),
                eq(request.getItems()),
                rawPayloadCaptor.capture(),
                eq(17));
        assertEquals("stripe", response.getSourceSystem());
        assertEquals(1, response.getImportedCount());
        assertEquals("settle-1", request.getItems().getFirst().getExternalReference());
        assertEquals(new BigDecimal("100.1230"), request.getItems().getFirst().getAmount());
        String rawPayloadJson = rawPayloadCaptor.getValue().getFirst();
        assertTrue(rawPayloadJson.equals("{\"line\":1,\"provider\":\"stripe\"}")
                || rawPayloadJson.equals("{\"provider\":\"stripe\",\"line\":1}"));
    }

    @Test
    void importSourceItemsRejectsInvalidCurrency() {
        FinanceReconciliationSourceImportRequest request = sourceImportRequest();
        request.getItems().getFirst().setCurrencyCode("egp");

        ApiException exception = assertThrows(ApiException.class, () ->
                service.importSourceItemsForAuthenticatedUser("sam", request));

        assertEquals("FINANCE_CURRENCY_INVALID", exception.getCode());
    }

    @Test
    void importSourceItemsNormalizesPayMobRawPayloadWhenStandardFieldsAreMissing() {
        FinanceReconciliationSourceImportItemRequest item = new FinanceReconciliationSourceImportItemRequest(
                null,
                null,
                null,
                null,
                null,
                Map.of(
                        "obj", Map.of(
                                "id", 998877,
                                "created_at", "2026-04-21T09:15:30Z",
                                "amount_cents", 12345,
                                "currency", "egp",
                                "source_data", Map.of("type", "wallet")),
                        "providerCode", "paymob"));
        FinanceReconciliationSourceImportRequest request = new FinanceReconciliationSourceImportRequest(
                COMPANY_ID,
                BRANCH_ID,
                "Card_Settlement",
                " PayMob ",
                new ArrayList<>(List.of(item)));
        FinanceReconciliationSourceItem imported = new FinanceReconciliationSourceItem(
                SOURCE_ITEM_ID,
                COMPANY_ID,
                BRANCH_ID,
                "card_settlement",
                "paymob",
                "998877",
                LocalDate.of(2026, 4, 21),
                new BigDecimal("123.4500"),
                "EGP",
                "PayMob wallet settlement",
                "{\"obj\":{\"id\":998877,\"created_at\":\"2026-04-21T09:15:30Z\",\"amount_cents\":12345,\"currency\":\"egp\",\"source_data\":{\"type\":\"wallet\"}},\"providerCode\":\"paymob\"}",
                "imported",
                Instant.now(),
                17,
                Instant.now(),
                17);
        when(dbFinanceReconciliation.importSourceItems(
                eq(COMPANY_ID),
                eq(BRANCH_ID),
                eq("card_settlement"),
                eq("paymob"),
                eq(request.getItems()),
                any(),
                eq(17))).thenReturn(new ArrayList<>(List.of(imported)));

        FinanceReconciliationSourceImportResponse response = service.importSourceItemsForAuthenticatedUser("sam", request);

        assertEquals("998877", item.getExternalReference());
        assertEquals(LocalDate.of(2026, 4, 21), item.getSourceDate());
        assertEquals(new BigDecimal("123.4500"), item.getAmount());
        assertEquals("EGP", item.getCurrencyCode());
        assertEquals("PayMob wallet settlement", item.getDescription());
        assertEquals("paymob", response.getSourceSystem());
    }

    @Test
    void importSourceItemsAutoEnqueuesCardSettlementPostingRequest() {
        FinanceReconciliationSourceImportRequest request = sourceImportRequest();
        when(dbFinanceReconciliation.importSourceItems(
                eq(COMPANY_ID),
                eq(BRANCH_ID),
                eq("card_settlement"),
                eq("stripe"),
                eq(request.getItems()),
                any(),
                eq(17))).thenReturn(new ArrayList<>(List.of(sourceItem())));

        service.importSourceItemsForAuthenticatedUser("sam", request);

        verify(financeOperationalPostingService).enqueueImportedProviderSettlement(
                eq(COMPANY_ID),
                eq(BRANCH_ID),
                eq("card_settlement"),
                eq("reconciliation-source:stripe:settle-1"),
                eq(new BigDecimal("100.1200")),
                eq(new BigDecimal("0.0000")),
                eq(new BigDecimal("100.1200")),
                eq("card"),
                eq("bank"),
                eq("settle-1"),
                any(),
                eq("system"),
                any());
    }

    @Test
    void importSourceItemsAutoEnqueuesWalletSettlementWhenRawPayloadSaysWallet() {
        FinanceReconciliationSourceImportRequest request = sourceImportRequest();
        request.getItems().getFirst().setRawPayload(Map.of(
                "settlementMethod", "wallet",
                "settledAmount", 490.0,
                "providerFeeAmount", 10.0,
                "providerReference", "wallet-settle-1"));
        FinanceReconciliationSourceItem imported = sourceItem();
        imported.setRawPayloadJson("{\"settlementMethod\":\"wallet\",\"settledAmount\":490.0,\"providerFeeAmount\":10.0,\"providerReference\":\"wallet-settle-1\"}");
        when(dbFinanceReconciliation.importSourceItems(
                eq(COMPANY_ID),
                eq(BRANCH_ID),
                eq("card_settlement"),
                eq("stripe"),
                eq(request.getItems()),
                any(),
                eq(17))).thenReturn(new ArrayList<>(List.of(imported)));

        service.importSourceItemsForAuthenticatedUser("sam", request);

        verify(financeOperationalPostingService).enqueueImportedProviderSettlement(
                eq(COMPANY_ID),
                eq(BRANCH_ID),
                eq("wallet_settlement"),
                eq("reconciliation-source:stripe:settle-1"),
                eq(new BigDecimal("100.1200")),
                eq(new BigDecimal("10.0000")),
                eq(new BigDecimal("490.0000")),
                eq("wallet"),
                eq("bank"),
                eq("wallet-settle-1"),
                any(),
                eq("system"),
                any());
    }

    @Test
    void updateItemResolutionRequiresResolvedOrDismissedNote() {
        when(dbFinanceReconciliation.getItemById(COMPANY_ID, ITEM_ID)).thenReturn(item(RUN_ID));
        when(dbFinanceReconciliation.getRunById(COMPANY_ID, RUN_ID)).thenReturn(run("completed_with_exceptions", "bank"));

        ApiException exception = assertThrows(ApiException.class, () ->
                service.updateItemResolutionForAuthenticatedUser(
                        "sam",
                        RUN_ID,
                        ITEM_ID,
                        new FinanceReconciliationItemResolutionRequest(COMPANY_ID, "resolved", " ", null, null, null, null)));

        assertEquals("FINANCE_RECONCILIATION_RESOLUTION_NOTE_REQUIRED", exception.getCode());
    }

    @Test
    void updateItemResolutionRejectsItemFromDifferentRun() {
        when(dbFinanceReconciliation.getItemById(COMPANY_ID, ITEM_ID)).thenReturn(item(OTHER_RUN_ID));

        ApiException exception = assertThrows(ApiException.class, () ->
                service.updateItemResolutionForAuthenticatedUser(
                        "sam",
                        RUN_ID,
                        ITEM_ID,
                        new FinanceReconciliationItemResolutionRequest(COMPANY_ID, "proposed", null, null, null, null, null)));

        assertEquals("FINANCE_RECONCILIATION_ITEM_RUN_MISMATCH", exception.getCode());
    }

    @Test
    void updateItemResolutionPersistsResolutionAndRefreshesRunDifference() {
        FinanceReconciliationItemItem existing = item(RUN_ID);
        FinanceReconciliationItemItem updated = item(RUN_ID);
        updated.setResolutionStatus("resolved");
        updated.setResolutionNote("Matched with provider support evidence");
        when(dbFinanceReconciliation.getItemById(COMPANY_ID, ITEM_ID)).thenReturn(existing);
        when(dbFinanceReconciliation.getRunById(COMPANY_ID, RUN_ID)).thenReturn(run("completed_with_exceptions", "bank"));
        FinanceReconciliationItemResolutionRequest request = new FinanceReconciliationItemResolutionRequest(
                COMPANY_ID, "resolved", "Matched with provider support evidence", null, null, null, null);
        when(dbFinanceReconciliation.updateItemResolution(
                eq(COMPANY_ID),
                eq(ITEM_ID),
                eq("resolved"),
                eq("Matched with provider support evidence"),
                any(),
                eq(17))).thenReturn(updated);

        FinanceReconciliationItemItem response = service.updateItemResolutionForAuthenticatedUser(
                "sam",
                RUN_ID,
                ITEM_ID,
                new FinanceReconciliationItemResolutionRequest(
                        COMPANY_ID,
                        "resolved",
                        " Matched with provider support evidence ",
                        null, null, null, null));

        assertEquals("resolved", response.getResolutionStatus());
        verify(dbFinanceReconciliation).refreshRunDifference(COMPANY_ID, RUN_ID, 17);
    }

    private FinanceReconciliationRunCreateRequest runCreateRequest(String reconciliationType) {
        return new FinanceReconciliationRunCreateRequest(
                COMPANY_ID,
                BRANCH_ID,
                reconciliationType,
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 20));
    }

    private FinanceReconciliationSourceImportRequest sourceImportRequest() {
        ArrayList<FinanceReconciliationSourceImportItemRequest> items = new ArrayList<>();
        items.add(new FinanceReconciliationSourceImportItemRequest(
                " Settle-1 ",
                LocalDate.of(2026, 4, 10),
                new BigDecimal("100.123"),
                "EGP",
                " Provider settlement ",
                Map.of("provider", "stripe", "line", 1)));
        return new FinanceReconciliationSourceImportRequest(
                COMPANY_ID,
                BRANCH_ID,
                "Card_Settlement",
                " Stripe ",
                items);
    }

    private FinanceReconciliationRunItem run(String status, String reconciliationType) {
        return new FinanceReconciliationRunItem(
                RUN_ID,
                COMPANY_ID,
                BRANCH_ID,
                reconciliationType,
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 20),
                status,
                new BigDecimal("12.3400"),
                17,
                Instant.now(),
                Instant.now(),
                Instant.now(),
                17,
                Instant.now(),
                17);
    }

    private FinanceReconciliationSourceItem sourceItem() {
        return new FinanceReconciliationSourceItem(
                SOURCE_ITEM_ID,
                COMPANY_ID,
                BRANCH_ID,
                "card_settlement",
                "stripe",
                "settle-1",
                LocalDate.of(2026, 4, 10),
                new BigDecimal("100.1200"),
                "EGP",
                "Provider settlement",
                "{\"provider\":\"stripe\",\"line\":1}",
                "imported",
                Instant.now(),
                17,
                Instant.now(),
                17);
    }

    private FinanceReconciliationItemItem item(UUID runId) {
        return new FinanceReconciliationItemItem(
                ITEM_ID,
                COMPANY_ID,
                runId,
                SOURCE_ITEM_ID,
                "imported_source",
                "settle-1",
                LEDGER_LINE_ID,
                "difference",
                new BigDecimal("12.3400"),
                "unresolved",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Instant.now(),
                17,
                Instant.now(),
                17);
    }
}
