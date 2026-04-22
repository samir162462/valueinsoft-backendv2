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

class FinancePosPostingAdapterTest {

    private static final int COMPANY_ID = 7;
    private static final int BRANCH_ID = 3;
    private static final UUID FISCAL_PERIOD_ID = UUID.fromString("00000000-0000-0000-0000-000000001001");
    private static final UUID JOURNAL_ID = UUID.fromString("00000000-0000-0000-0000-000000001002");
    private static final UUID SALES_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000001101");
    private static final UUID CASH_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000001102");
    private static final UUID CARD_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000001103");
    private static final UUID DISCOUNT_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000001104");
    private static final UUID OUTPUT_VAT_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000001105");
    private static final UUID COGS_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000001106");
    private static final UUID INVENTORY_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000001107");
    private static final UUID SALES_RETURNS_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000001108");
    private static final UUID DAMAGE_EXPENSE_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000001109");

    private DbFinanceSetup dbFinanceSetup;
    private DbFinanceJournal dbFinanceJournal;
    private FinancePosPostingAdapter adapter;

    @BeforeEach
    void setUp() {
        dbFinanceSetup = Mockito.mock(DbFinanceSetup.class);
        dbFinanceJournal = Mockito.mock(DbFinanceJournal.class);
        adapter = new FinancePosPostingAdapter(dbFinanceSetup, dbFinanceJournal, new ObjectMapper());

        stubMapping("pos.sales", SALES_ACCOUNT_ID);
        stubMapping("pos.cash", CASH_ACCOUNT_ID);
        stubMapping("pos.card", CARD_ACCOUNT_ID);
        stubMapping("pos.discount", DISCOUNT_ACCOUNT_ID);
        stubMapping("pos.output_vat", OUTPUT_VAT_ACCOUNT_ID);
        stubMapping("pos.cogs", COGS_ACCOUNT_ID);
        stubMapping("pos.inventory", INVENTORY_ACCOUNT_ID);
        stubMapping("pos.sales_returns", SALES_RETURNS_ACCOUNT_ID);
        stubMapping("inventory.damage_expense", DAMAGE_EXPENSE_ACCOUNT_ID);
        when(dbFinanceJournal.allocateSourceJournalNumber(COMPANY_ID, "pos.sales", "PS-"))
                .thenReturn("PS-000001");
        when(dbFinanceJournal.allocateSourceJournalNumber(COMPANY_ID, "pos.sales_return", "SR-"))
                .thenReturn("SR-000001");
        when(dbFinanceJournal.createPostedSourceJournal(any())).thenReturn(JOURNAL_ID);
    }

    @Test
    void postCreatesBalancedSaleJournalWithVatDiscountPaymentsAndCogs() {
        UUID journalId = adapter.post(request("""
                {
                  "currencyCode": "EGP",
                  "netAmount": 115.0000,
                  "salesAmount": 100.0000,
                  "discountAmount": 5.0000,
                  "taxAmount": 20.0000,
                  "customerId": 44,
                  "payments": [
                    {"method": "cash", "amount": 70.0000, "paymentId": "cash-1"},
                    {"method": "card", "amount": 45.0000, "paymentId": "card-1"}
                  ],
                  "items": [
                    {"productId": 501, "quantity": 2, "unitCost": 15.0000, "inventoryMovementId": 9001},
                    {"productId": 502, "totalCost": 10.0000, "stockLedgerId": 9002}
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
        assertEquals("PS-000001", command.journalNumber());
        assertEquals("sales", command.journalType());
        assertEquals("pos", command.sourceModule());
        assertEquals("sale", command.sourceType());
        assertEquals("SALE-1001", command.sourceId());
        assertEquals(LocalDate.of(2026, 2, 10), command.postingDate());
        assertEquals(FISCAL_PERIOD_ID, command.fiscalPeriodId());
        assertEquals("EGP", command.currencyCode());
        assertEquals(new BigDecimal("160.0000"), command.totalDebit());
        assertEquals(new BigDecimal("160.0000"), command.totalCredit());
        assertEquals(9, command.lines().size());

        assertLine(command.lines().get(0), CASH_ACCOUNT_ID, money("70.0000"), money("0.0000"), 44, null, null, "cash-1");
        assertLine(command.lines().get(1), CARD_ACCOUNT_ID, money("45.0000"), money("0.0000"), 44, null, null, "card-1");
        assertLine(command.lines().get(2), DISCOUNT_ACCOUNT_ID, money("5.0000"), money("0.0000"), 44, null, null, null);
        assertLine(command.lines().get(3), SALES_ACCOUNT_ID, money("0.0000"), money("100.0000"), 44, null, null, null);
        assertLine(command.lines().get(4), OUTPUT_VAT_ACCOUNT_ID, money("0.0000"), money("20.0000"), 44, null, null, null);
        assertLine(command.lines().get(5), COGS_ACCOUNT_ID, money("30.0000"), money("0.0000"), 44, 501L, 9001L, null);
        assertLine(command.lines().get(6), INVENTORY_ACCOUNT_ID, money("0.0000"), money("30.0000"), 44, 501L, 9001L, null);
        assertLine(command.lines().get(7), COGS_ACCOUNT_ID, money("10.0000"), money("0.0000"), 44, 502L, 9002L, null);
        assertLine(command.lines().get(8), INVENTORY_ACCOUNT_ID, money("0.0000"), money("10.0000"), 44, 502L, 9002L, null);

        verify(dbFinanceJournal).applyPostedJournalToAccountBalances(COMPANY_ID, JOURNAL_ID, 17);
    }

    @Test
    void postRejectsPaymentTotalsThatDoNotMatchNetAmount() {
        ApiException exception = assertThrows(ApiException.class, () -> adapter.post(request("""
                {
                  "netAmount": 115.0000,
                  "salesAmount": 100.0000,
                  "taxAmount": 15.0000,
                  "payments": [
                    {"method": "cash", "amount": 100.0000}
                  ]
                }
                """)));

        assertEquals("FINANCE_POS_PAYMENT_TOTAL_MISMATCH", exception.getCode());
    }

    @Test
    void postRejectsHeaderCogsThatDoesNotMatchItemCosts() {
        ApiException exception = assertThrows(ApiException.class, () -> adapter.post(request("""
                {
                  "netAmount": 115.0000,
                  "salesAmount": 100.0000,
                  "taxAmount": 15.0000,
                  "paymentMethod": "cash",
                  "cogsAmount": 50.0000,
                  "items": [
                    {"productId": 501, "quantity": 2, "unitCost": 15.0000}
                  ]
                }
                """)));

        assertEquals("FINANCE_POS_COGS_TOTAL_MISMATCH", exception.getCode());
    }

    @Test
    void postCreatesBalancedSaleReturnJournalForSellableReturnedStock() {
        UUID journalId = adapter.post(request("sale_return", "RETURN-1001", """
                {
                  "currencyCode": "EGP",
                  "refundAmount": 115.0000,
                  "salesReturnAmount": 100.0000,
                  "taxAmount": 15.0000,
                  "customerId": 44,
                  "refundMethod": "cash",
                  "paymentId": "cash-refund-1",
                  "returnedToStock": true,
                  "items": [
                    {"productId": 501, "quantity": 2, "unitCost": 20.0000, "inventoryMovementId": 9101}
                  ]
                }
                """));

        assertEquals(JOURNAL_ID, journalId);

        ArgumentCaptor<DbFinanceJournal.PostedSourceJournalCommand> commandCaptor =
                ArgumentCaptor.forClass(DbFinanceJournal.PostedSourceJournalCommand.class);
        verify(dbFinanceJournal).createPostedSourceJournal(commandCaptor.capture());

        DbFinanceJournal.PostedSourceJournalCommand command = commandCaptor.getValue();
        assertEquals("SR-000001", command.journalNumber());
        assertEquals("sales_return", command.journalType());
        assertEquals("sale_return", command.sourceType());
        assertEquals("RETURN-1001", command.sourceId());
        assertEquals(new BigDecimal("155.0000"), command.totalDebit());
        assertEquals(new BigDecimal("155.0000"), command.totalCredit());
        assertEquals(5, command.lines().size());

        assertLine(command.lines().get(0), SALES_RETURNS_ACCOUNT_ID, money("100.0000"), money("0.0000"), 44, null, null, null);
        assertLine(command.lines().get(1), OUTPUT_VAT_ACCOUNT_ID, money("15.0000"), money("0.0000"), 44, null, null, null);
        assertLine(command.lines().get(2), CASH_ACCOUNT_ID, money("0.0000"), money("115.0000"), 44, null, null, "cash-refund-1");
        assertLine(command.lines().get(3), INVENTORY_ACCOUNT_ID, money("40.0000"), money("0.0000"), 44, 501L, 9101L, null);
        assertLine(command.lines().get(4), COGS_ACCOUNT_ID, money("0.0000"), money("40.0000"), 44, 501L, 9101L, null);
    }

    @Test
    void postCreatesSaleReturnJournalForNonSellableReturnedStock() {
        adapter.post(request("sale_return", "RETURN-1002", """
                {
                  "refundAmount": 100.0000,
                  "taxAmount": 0.0000,
                  "customerId": 44,
                  "refundMethod": "cash",
                  "returnedToStock": false,
                  "items": [
                    {"productId": 501, "totalCost": 40.0000}
                  ]
                }
                """));

        ArgumentCaptor<DbFinanceJournal.PostedSourceJournalCommand> commandCaptor =
                ArgumentCaptor.forClass(DbFinanceJournal.PostedSourceJournalCommand.class);
        verify(dbFinanceJournal).createPostedSourceJournal(commandCaptor.capture());

        DbFinanceJournal.PostedSourceJournalCommand command = commandCaptor.getValue();
        assertEquals(new BigDecimal("140.0000"), command.totalDebit());
        assertEquals(new BigDecimal("140.0000"), command.totalCredit());
        assertLine(command.lines().get(1), CASH_ACCOUNT_ID, money("0.0000"), money("100.0000"), 44, null, null, null);
        assertLine(command.lines().get(2), DAMAGE_EXPENSE_ACCOUNT_ID, money("40.0000"), money("0.0000"), 44, 501L, null, null);
        assertLine(command.lines().get(3), COGS_ACCOUNT_ID, money("0.0000"), money("40.0000"), 44, 501L, null, null);
    }

    @Test
    void postRejectsRefundTotalsThatDoNotMatchReturnAmount() {
        ApiException exception = assertThrows(ApiException.class, () -> adapter.post(request("sale_return", "RETURN-1003", """
                {
                  "refundAmount": 115.0000,
                  "salesReturnAmount": 100.0000,
                  "taxAmount": 15.0000,
                  "refunds": [
                    {"method": "cash", "amount": 100.0000}
                  ]
                }
                """)));

        assertEquals("FINANCE_POS_REFUND_TOTAL_MISMATCH", exception.getCode());
    }

    @Test
    void postRejectsMissingAccountMapping() {
        when(dbFinanceSetup.resolveActiveAccountMapping(COMPANY_ID, BRANCH_ID, "pos.output_vat",
                LocalDate.of(2026, 2, 10))).thenReturn(null);

        ApiException exception = assertThrows(ApiException.class, () -> adapter.post(request("""
                {
                  "netAmount": 115.0000,
                  "salesAmount": 100.0000,
                  "taxAmount": 15.0000,
                  "paymentMethod": "cash"
                }
                """)));

        assertEquals("FINANCE_ACCOUNT_MAPPING_MISSING", exception.getCode());
    }

    private void assertLine(DbFinanceJournal.PostedSourceJournalLineCommand line,
                            UUID accountId,
                            BigDecimal debit,
                            BigDecimal credit,
                            Integer customerId,
                            Long productId,
                            Long inventoryMovementId,
                            String paymentId) {
        assertEquals(accountId, line.accountId());
        assertEquals(BRANCH_ID, line.branchId());
        assertEquals(debit, line.debitAmount());
        assertEquals(credit, line.creditAmount());
        assertEquals(customerId, line.customerId());
        assertEquals(productId, line.productId());
        assertEquals(inventoryMovementId, line.inventoryMovementId());
        assertEquals(paymentId, line.paymentId());
    }

    private void stubMapping(String mappingKey, UUID accountId) {
        when(dbFinanceSetup.resolveActiveAccountMapping(COMPANY_ID, BRANCH_ID, mappingKey,
                LocalDate.of(2026, 2, 10))).thenReturn(mapping(mappingKey, accountId));
    }

    private FinanceAccountMappingItem mapping(String mappingKey, UUID accountId) {
        return new FinanceAccountMappingItem(
                UUID.randomUUID(),
                COMPANY_ID,
                BRANCH_ID,
                mappingKey,
                accountId,
                mappingKey,
                mappingKey,
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

    private FinancePostingRequestItem request(String payload) {
        return request("sale", "SALE-1001", payload);
    }

    private FinancePostingRequestItem request(String sourceType, String sourceId, String payload) {
        return new FinancePostingRequestItem(
                UUID.fromString("00000000-0000-0000-0000-000000001201"),
                COMPANY_ID,
                BRANCH_ID,
                null,
                "pos",
                sourceType,
                sourceId,
                LocalDate.of(2026, 2, 10),
                FISCAL_PERIOD_ID,
                "hash-1",
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
