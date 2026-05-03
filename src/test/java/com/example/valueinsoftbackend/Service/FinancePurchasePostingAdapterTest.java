package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbFinanceJournal;
import com.example.valueinsoftbackend.DatabaseRequests.DbFinanceSetup;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Finance.FinanceAccountMappingItem;
import com.example.valueinsoftbackend.Model.Finance.FinancePostingRequestItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinancePurchasePostingAdapterTest {

    private static final int COMPANY_ID = 7;
    private static final int BRANCH_ID = 3;
    private static final UUID FISCAL_PERIOD_ID = UUID.fromString("00000000-0000-0000-0000-000000002001");
    private static final UUID JOURNAL_ID = UUID.fromString("00000000-0000-0000-0000-000000002002");
    private static final UUID INVENTORY_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000002101");
    private static final UUID INPUT_VAT_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000002102");
    private static final UUID PAYABLE_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000002103");
    private static final UUID GRNI_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000002104");
    private static final UUID CASH_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000002105");

    private DbFinanceSetup dbFinanceSetup;
    private DbFinanceJournal dbFinanceJournal;
    private FinancePurchasePostingAdapter adapter;

    @BeforeEach
    void setUp() {
        dbFinanceSetup = Mockito.mock(DbFinanceSetup.class);
        dbFinanceJournal = Mockito.mock(DbFinanceJournal.class);
        adapter = new FinancePurchasePostingAdapter(dbFinanceSetup, dbFinanceJournal, new ObjectMapper());

        stubMapping("purchase.inventory", INVENTORY_ACCOUNT_ID);
        stubMapping("purchase.input_vat", INPUT_VAT_ACCOUNT_ID);
        stubMapping("purchase.payable", PAYABLE_ACCOUNT_ID);
        stubMapping("purchase.grni", GRNI_ACCOUNT_ID);
        stubMapping("purchase.cash", CASH_ACCOUNT_ID);
        when(dbFinanceJournal.allocateSourceJournalNumber(COMPANY_ID, "purchase.invoice", "PI-"))
                .thenReturn("PI-000001");
        when(dbFinanceJournal.createPostedSourceJournal(any())).thenReturn(JOURNAL_ID);
    }

    @Test
    void postPurchaseInvoiceCreatesInventoryVatPaymentAndPayableLines() {
        UUID journalId = adapter.post(request("purchase_invoice", """
                {
                  "currencyCode": "EGP",
                  "supplierId": 88,
                  "grossAmount": 230.0000,
                  "taxAmount": 30.0000,
                  "paidAmount": 50.0000,
                  "paymentMethod": "cash",
                  "paymentId": "cash-pay-1",
                  "items": [
                    {"productId": 701, "quantity": 2, "unitCost": 60.0000, "inventoryMovementId": 3001},
                    {"productId": 702, "totalCost": 80.0000, "stockLedgerId": 3002}
                  ]
                }
                """));

        assertEquals(JOURNAL_ID, journalId);

        ArgumentCaptor<DbFinanceJournal.PostedSourceJournalCommand> commandCaptor =
                ArgumentCaptor.forClass(DbFinanceJournal.PostedSourceJournalCommand.class);
        verify(dbFinanceJournal).createPostedSourceJournal(commandCaptor.capture());

        DbFinanceJournal.PostedSourceJournalCommand command = commandCaptor.getValue();
        assertEquals(COMPANY_ID, command.companyId());
        assertEquals(BRANCH_ID, command.branchId());
        assertEquals("PI-000001", command.journalNumber());
        assertEquals("purchase", command.journalType());
        assertEquals("purchase", command.sourceModule());
        assertEquals("purchase_invoice", command.sourceType());
        assertEquals("PUR-2001", command.sourceId());
        assertEquals(LocalDate.of(2026, 3, 5), command.postingDate());
        assertEquals(FISCAL_PERIOD_ID, command.fiscalPeriodId());
        assertEquals("EGP", command.currencyCode());
        assertEquals(money("230.0000"), command.totalDebit());
        assertEquals(money("230.0000"), command.totalCredit());
        assertEquals(5, command.lines().size());

        assertLine(command.lines().get(0), INVENTORY_ACCOUNT_ID, money("120.0000"), money("0.0000"), 88, 701L, 3001L, null);
        assertLine(command.lines().get(1), INVENTORY_ACCOUNT_ID, money("80.0000"), money("0.0000"), 88, 702L, 3002L, null);
        assertLine(command.lines().get(2), INPUT_VAT_ACCOUNT_ID, money("30.0000"), money("0.0000"), 88, null, null, null);
        assertLine(command.lines().get(3), CASH_ACCOUNT_ID, money("0.0000"), money("50.0000"), 88, null, null, "cash-pay-1");
        assertLine(command.lines().get(4), PAYABLE_ACCOUNT_ID, money("0.0000"), money("180.0000"), 88, null, null, null);

        verify(dbFinanceJournal).applyPostedJournalToAccountBalances(COMPANY_ID, JOURNAL_ID, 17);
    }

    @Test
    void postGoodsReceiptCreditsGrniInsteadOfPayable() {
        adapter.post(request("goods_receipt", """
                {
                  "supplierId": 88,
                  "inventoryAmount": 200.0000,
                  "taxAmount": 0.0000,
                  "grossAmount": 200.0000
                }
                """));

        ArgumentCaptor<DbFinanceJournal.PostedSourceJournalCommand> commandCaptor =
                ArgumentCaptor.forClass(DbFinanceJournal.PostedSourceJournalCommand.class);
        verify(dbFinanceJournal).createPostedSourceJournal(commandCaptor.capture());

        DbFinanceJournal.PostedSourceJournalCommand command = commandCaptor.getValue();
        assertEquals("goods_receipt", command.sourceType());
        assertEquals(2, command.lines().size());
        assertLine(command.lines().get(0), INVENTORY_ACCOUNT_ID, money("200.0000"), money("0.0000"), 88, null, null, null);
        assertLine(command.lines().get(1), GRNI_ACCOUNT_ID, money("0.0000"), money("200.0000"), 88, null, null, null);
    }

    @Test
    void postPurchaseReturnCreatesSupplierCreditAndInventoryReductionLines() {
        when(dbFinanceJournal.allocateSourceJournalNumber(COMPANY_ID, "purchase.return", "PR-"))
                .thenReturn("PR-000001");

        UUID journalId = adapter.post(request("purchase_return", """
                {
                  "currencyCode": "EGP",
                  "supplierId": 88,
                  "returnAmount": 200.0000,
                  "taxAmount": 30.0000,
                  "refundedAmount": 50.0000,
                  "paymentMethod": "cash",
                  "paymentId": "cash-refund-1",
                  "items": [
                    {"productId": 701, "quantity": 2, "unitCost": 60.0000, "inventoryMovementId": 3001},
                    {"productId": 702, "totalCost": 80.0000, "stockLedgerId": 3002}
                  ]
                }
                """));

        assertEquals(JOURNAL_ID, journalId);

        ArgumentCaptor<DbFinanceJournal.PostedSourceJournalCommand> commandCaptor =
                ArgumentCaptor.forClass(DbFinanceJournal.PostedSourceJournalCommand.class);
        verify(dbFinanceJournal).createPostedSourceJournal(commandCaptor.capture());

        DbFinanceJournal.PostedSourceJournalCommand command = commandCaptor.getValue();
        assertEquals("PR-000001", command.journalNumber());
        assertEquals("purchase_return", command.journalType());
        assertEquals("purchase_return", command.sourceType());
        assertEquals(money("230.0000"), command.totalDebit());
        assertEquals(money("230.0000"), command.totalCredit());
        assertEquals(5, command.lines().size());

        assertLine(command.lines().get(0), INVENTORY_ACCOUNT_ID, money("0.0000"), money("120.0000"), 88, 701L, 3001L, null);
        assertLine(command.lines().get(1), INVENTORY_ACCOUNT_ID, money("0.0000"), money("80.0000"), 88, 702L, 3002L, null);
        assertLine(command.lines().get(2), INPUT_VAT_ACCOUNT_ID, money("0.0000"), money("30.0000"), 88, null, null, null);
        assertLine(command.lines().get(3), CASH_ACCOUNT_ID, money("50.0000"), money("0.0000"), 88, null, null, "cash-refund-1");
        assertLine(command.lines().get(4), PAYABLE_ACCOUNT_ID, money("180.0000"), money("0.0000"), 88, null, null, null);

        verify(dbFinanceJournal).applyPostedJournalToAccountBalances(COMPANY_ID, JOURNAL_ID, 17);
    }

    @Test
    void postRejectsMissingSupplier() {
        ApiException exception = assertThrows(ApiException.class, () -> adapter.post(request("purchase_invoice", """
                {
                  "inventoryAmount": 100.0000,
                  "grossAmount": 100.0000
                }
                """)));

        assertEquals("FINANCE_PURCHASE_SUPPLIER_REQUIRED", exception.getCode());
    }

    @Test
    void postRejectsInventoryTotalMismatch() {
        ApiException exception = assertThrows(ApiException.class, () -> adapter.post(request("purchase_invoice", """
                {
                  "supplierId": 88,
                  "inventoryAmount": 150.0000,
                  "grossAmount": 150.0000,
                  "items": [
                    {"productId": 701, "quantity": 2, "unitCost": 60.0000}
                  ]
                }
                """)));

        assertEquals("FINANCE_PURCHASE_INVENTORY_TOTAL_MISMATCH", exception.getCode());
    }

    @Test
    void postRejectsPaidAmountGreaterThanGrossAmount() {
        ApiException exception = assertThrows(ApiException.class, () -> adapter.post(request("purchase_invoice", """
                {
                  "supplierId": 88,
                  "inventoryAmount": 100.0000,
                  "grossAmount": 100.0000,
                  "paidAmount": 110.0000
                }
                """)));

        assertEquals("FINANCE_PURCHASE_PAYMENT_AMOUNT_INVALID", exception.getCode());
    }

    private void assertLine(DbFinanceJournal.PostedSourceJournalLineCommand line,
                            UUID accountId,
                            BigDecimal debit,
                            BigDecimal credit,
                            Integer supplierId,
                            Long productId,
                            Long inventoryMovementId,
                            String paymentId) {
        assertEquals(accountId, line.accountId());
        assertEquals(BRANCH_ID, line.branchId());
        assertEquals(debit, line.debitAmount());
        assertEquals(credit, line.creditAmount());
        assertEquals(null, line.customerId());
        assertEquals(supplierId, line.supplierId());
        assertEquals(productId, line.productId());
        assertEquals(inventoryMovementId, line.inventoryMovementId());
        assertEquals(paymentId, line.paymentId());
    }

    private void stubMapping(String mappingKey, UUID accountId) {
        when(dbFinanceSetup.resolveActiveAccountMapping(eq(COMPANY_ID), eq(BRANCH_ID), any(), eq(mappingKey),
                eq(LocalDate.of(2026, 3, 5)))).thenReturn(mapping(mappingKey, accountId));
    }

    private FinanceAccountMappingItem mapping(String mappingKey, UUID accountId) {
        return new FinanceAccountMappingItem(
                UUID.randomUUID(),
                COMPANY_ID,
                BRANCH_ID,
                null,
                mappingKey,
                accountId,
                "ACC-" + mappingKey,
                "Account " + mappingKey,
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

    private FinancePostingRequestItem request(String sourceType, String payload) {
        return new FinancePostingRequestItem(
                UUID.fromString("00000000-0000-0000-0000-000000002201"),
                COMPANY_ID,
                BRANCH_ID,
                null,
                "purchase",
                sourceType,
                "PUR-2001",
                LocalDate.of(2026, 3, 5),
                FISCAL_PERIOD_ID,
                "hash-2",
                payload,
                "processing",
                1,
                Instant.now(),
                null,
                null,
                Instant.now(),
                15,
                Instant.now(),
                17);
    }

    private BigDecimal money(String value) {
        return new BigDecimal(value).setScale(4);
    }
}
