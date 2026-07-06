package com.example.valueinsoftbackend.Service.finance;

import com.example.valueinsoftbackend.DatabaseRequests.DbFinanceJournal;
import com.example.valueinsoftbackend.DatabaseRequests.DbFinanceSetup;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Finance.FinanceAccountMappingItem;
import com.example.valueinsoftbackend.Model.Finance.FinancePostingRequestItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Client trade-in receipts must post balanced journals:
 *   Debit  purchase.inventory        (full received value)
 *   Credit purchase.<method>         (paid amount)
 *   Credit purchase.client_payable   (remaining, carrying the customer dimension)
 */
class FinancePurchasePostingAdapterClientTradeInTest {

    private DbFinanceSetup dbFinanceSetup;
    private DbFinanceJournal dbFinanceJournal;
    private FinancePurchasePostingAdapter adapter;

    private final UUID inventoryAccount = UUID.randomUUID();
    private final UUID cashAccount = UUID.randomUUID();
    private final UUID clientPayableAccount = UUID.randomUUID();
    private final Map<String, UUID> mappingAccounts = new HashMap<>();

    @BeforeEach
    void setUp() {
        dbFinanceSetup = mock(DbFinanceSetup.class);
        dbFinanceJournal = mock(DbFinanceJournal.class);
        adapter = new FinancePurchasePostingAdapter(dbFinanceSetup, dbFinanceJournal, new ObjectMapper());

        mappingAccounts.put("purchase.inventory", inventoryAccount);
        mappingAccounts.put("purchase.cash", cashAccount);
        mappingAccounts.put("purchase.client_payable", clientPayableAccount);

        when(dbFinanceSetup.resolveActiveAccountMapping(anyInt(), any(), any(), anyString(), any()))
                .thenAnswer(invocation -> {
                    String mappingKey = invocation.getArgument(3);
                    UUID accountId = mappingAccounts.get(mappingKey);
                    if (accountId == null) {
                        return null;
                    }
                    FinanceAccountMappingItem item = new FinanceAccountMappingItem();
                    item.setMappingKey(mappingKey);
                    item.setAccountId(accountId);
                    return item;
                });
        when(dbFinanceJournal.allocateSourceJournalNumber(anyInt(), anyString(), anyString())).thenReturn("CT-000001");
        when(dbFinanceJournal.createPostedSourceJournal(any())).thenReturn(UUID.randomUUID());
    }

    private FinancePostingRequestItem request(String payload) {
        FinancePostingRequestItem item = new FinancePostingRequestItem();
        item.setPostingRequestId(UUID.randomUUID());
        item.setCompanyId(7);
        item.setBranchId(10000);
        item.setSourceModule("purchase");
        item.setSourceType("client_tradein_receipt");
        item.setSourceId("client-tradein-55");
        item.setPostingDate(LocalDate.of(2026, 7, 5));
        item.setFiscalPeriodId(UUID.randomUUID());
        item.setRequestPayloadJson(payload);
        item.setCreatedBy(1);
        return item;
    }

    private DbFinanceJournal.PostedSourceJournalCommand postAndCapture(String payload) {
        adapter.post(request(payload));
        ArgumentCaptor<DbFinanceJournal.PostedSourceJournalCommand> captor =
                ArgumentCaptor.forClass(DbFinanceJournal.PostedSourceJournalCommand.class);
        org.mockito.Mockito.verify(dbFinanceJournal).createPostedSourceJournal(captor.capture());
        return captor.getValue();
    }

    @Test
    void unpaidTradeInCreditsClientPayableForFullAmount() {
        DbFinanceJournal.PostedSourceJournalCommand command = postAndCapture("""
                {"currencyCode":"EGP","clientId":77,"inventoryAmount":1000,
                 "grossAmount":1000,"paidAmount":0,"paymentMethod":"cash",
                 "productId":5,"inventoryMovementId":90}
                """);

        assertEquals(0, command.totalDebit().compareTo(command.totalCredit()));
        assertEquals(0, command.totalDebit().compareTo(new BigDecimal("1000.0000")));
        assertEquals(2, command.lines().size());

        DbFinanceJournal.PostedSourceJournalLineCommand inventoryLine = command.lines().get(0);
        assertEquals(inventoryAccount, inventoryLine.accountId());
        assertEquals(0, inventoryLine.debitAmount().compareTo(new BigDecimal("1000.0000")));
        assertEquals(Integer.valueOf(77), inventoryLine.customerId());
        assertNull(inventoryLine.supplierId());

        DbFinanceJournal.PostedSourceJournalLineCommand payableLine = command.lines().get(1);
        assertEquals(clientPayableAccount, payableLine.accountId());
        assertEquals(0, payableLine.creditAmount().compareTo(new BigDecimal("1000.0000")));
        assertEquals(Integer.valueOf(77), payableLine.customerId());
    }

    @Test
    void partialPaymentSplitsCashAndClientPayable() {
        DbFinanceJournal.PostedSourceJournalCommand command = postAndCapture("""
                {"currencyCode":"EGP","clientId":77,"inventoryAmount":1000,
                 "grossAmount":1000,"paidAmount":400,"paymentMethod":"cash",
                 "productId":5,"inventoryMovementId":90}
                """);

        assertEquals(0, command.totalDebit().compareTo(command.totalCredit()));
        assertEquals(3, command.lines().size());
        BigDecimal cashCredit = command.lines().stream()
                .filter(line -> cashAccount.equals(line.accountId()))
                .map(DbFinanceJournal.PostedSourceJournalLineCommand::creditAmount)
                .findFirst().orElse(BigDecimal.ZERO);
        BigDecimal payableCredit = command.lines().stream()
                .filter(line -> clientPayableAccount.equals(line.accountId()))
                .map(DbFinanceJournal.PostedSourceJournalLineCommand::creditAmount)
                .findFirst().orElse(BigDecimal.ZERO);
        assertEquals(0, cashCredit.compareTo(new BigDecimal("400.0000")));
        assertEquals(0, payableCredit.compareTo(new BigDecimal("600.0000")));
    }

    @Test
    void fullPaymentHasNoPayableLine() {
        DbFinanceJournal.PostedSourceJournalCommand command = postAndCapture("""
                {"currencyCode":"EGP","clientId":77,"inventoryAmount":1000,
                 "grossAmount":1000,"paidAmount":1000,"paymentMethod":"cash",
                 "productId":5,"inventoryMovementId":90}
                """);

        assertEquals(0, command.totalDebit().compareTo(command.totalCredit()));
        assertEquals(2, command.lines().size());
        assertTrue(command.lines().stream().noneMatch(line -> clientPayableAccount.equals(line.accountId())));
    }

    @Test
    void tradeInWithoutClientIdIsRejected() {
        ApiException exception = assertThrows(ApiException.class, () -> adapter.post(request("""
                {"currencyCode":"EGP","inventoryAmount":1000,"grossAmount":1000,
                 "paidAmount":0,"paymentMethod":"cash"}
                """)));
        assertEquals("FINANCE_PURCHASE_CLIENT_REQUIRED", exception.getCode());
    }

    @Test
    void supplierInvoiceStillRequiresSupplierId() {
        FinancePostingRequestItem supplierRequest = request("""
                {"currencyCode":"EGP","inventoryAmount":1000,"grossAmount":1000,
                 "paidAmount":0,"paymentMethod":"cash"}
                """);
        supplierRequest.setSourceType("purchase_invoice");
        ApiException exception = assertThrows(ApiException.class, () -> adapter.post(supplierRequest));
        assertEquals("FINANCE_PURCHASE_SUPPLIER_REQUIRED", exception.getCode());
    }
}
