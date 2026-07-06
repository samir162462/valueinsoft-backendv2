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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Paying a client for trade-in stock settles the payable:
 *   Debit  purchase.client_payable (customer dimension)
 *   Credit payment.<instrument>
 */
class FinancePaymentPostingAdapterClientTradeInTest {

    private DbFinanceSetup dbFinanceSetup;
    private DbFinanceJournal dbFinanceJournal;
    private FinancePaymentPostingAdapter adapter;

    private final UUID clientPayableAccount = UUID.randomUUID();
    private final UUID cashDrawerAccount = UUID.randomUUID();
    private final Map<String, UUID> mappingAccounts = new HashMap<>();

    @BeforeEach
    void setUp() {
        dbFinanceSetup = mock(DbFinanceSetup.class);
        dbFinanceJournal = mock(DbFinanceJournal.class);
        adapter = new FinancePaymentPostingAdapter(dbFinanceSetup, dbFinanceJournal, new ObjectMapper());

        mappingAccounts.put("purchase.client_payable", clientPayableAccount);
        mappingAccounts.put("payment.cash_drawer", cashDrawerAccount);

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
        when(dbFinanceJournal.allocateSourceJournalNumber(anyInt(), anyString(), anyString())).thenReturn("CP-000001");
        when(dbFinanceJournal.createPostedSourceJournal(any())).thenReturn(UUID.randomUUID());
    }

    private FinancePostingRequestItem request(String payload) {
        FinancePostingRequestItem item = new FinancePostingRequestItem();
        item.setPostingRequestId(UUID.randomUUID());
        item.setCompanyId(7);
        item.setBranchId(10000);
        item.setSourceModule("payment");
        item.setSourceType("client_tradein_payment");
        item.setSourceId("client-tradein-payment-9");
        item.setPostingDate(LocalDate.of(2026, 7, 5));
        item.setFiscalPeriodId(UUID.randomUUID());
        item.setRequestPayloadJson(payload);
        item.setCreatedBy(1);
        return item;
    }

    @Test
    void clientTradeInPaymentPostsBalancedJournal() {
        adapter.post(request("""
                {"currencyCode":"EGP","clientId":77,"amountPaid":600,
                 "paymentMethod":"cash","paymentId":"client-tradein-payment-9"}
                """));

        ArgumentCaptor<DbFinanceJournal.PostedSourceJournalCommand> captor =
                ArgumentCaptor.forClass(DbFinanceJournal.PostedSourceJournalCommand.class);
        verify(dbFinanceJournal).createPostedSourceJournal(captor.capture());
        DbFinanceJournal.PostedSourceJournalCommand command = captor.getValue();

        assertEquals(0, command.totalDebit().compareTo(command.totalCredit()));
        assertEquals(0, command.totalDebit().compareTo(new BigDecimal("600.0000")));
        assertEquals(2, command.lines().size());

        DbFinanceJournal.PostedSourceJournalLineCommand debit = command.lines().get(0);
        assertEquals(clientPayableAccount, debit.accountId());
        assertEquals(0, debit.debitAmount().compareTo(new BigDecimal("600.0000")));
        assertEquals(Integer.valueOf(77), debit.customerId());
        assertNull(debit.supplierId());

        DbFinanceJournal.PostedSourceJournalLineCommand credit = command.lines().get(1);
        assertEquals(cashDrawerAccount, credit.accountId());
        assertEquals(0, credit.creditAmount().compareTo(new BigDecimal("600.0000")));
        assertEquals(Integer.valueOf(77), credit.customerId());
    }

    @Test
    void paymentWithoutClientIdIsRejected() {
        ApiException exception = assertThrows(ApiException.class, () -> adapter.post(request("""
                {"currencyCode":"EGP","amountPaid":600,"paymentMethod":"cash"}
                """)));
        assertEquals("FINANCE_CLIENT_TRADEIN_PAYMENT_CLIENT_REQUIRED", exception.getCode());
    }

    @Test
    void paymentWithoutPositiveAmountIsRejected() {
        ApiException exception = assertThrows(ApiException.class, () -> adapter.post(request("""
                {"currencyCode":"EGP","clientId":77,"amountPaid":0,"paymentMethod":"cash"}
                """)));
        assertEquals("FINANCE_CLIENT_TRADEIN_PAYMENT_AMOUNT_REQUIRED", exception.getCode());
    }
}
