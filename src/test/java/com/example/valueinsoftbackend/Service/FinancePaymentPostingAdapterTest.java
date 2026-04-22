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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinancePaymentPostingAdapterTest {

    private static final int COMPANY_ID = 7;
    private static final int BRANCH_ID = 3;
    private static final UUID FISCAL_PERIOD_ID = UUID.fromString("00000000-0000-0000-0000-000000004001");
    private static final UUID JOURNAL_ID = UUID.fromString("00000000-0000-0000-0000-000000004002");
    private static final UUID BANK_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000004101");
    private static final UUID CASH_SAFE_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000004102");
    private static final UUID CARD_CLEARING_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000004103");
    private static final UUID WALLET_CLEARING_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000004104");
    private static final UUID CASH_DRAWER_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000004105");
    private static final UUID FEE_EXPENSE_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000004106");

    private DbFinanceSetup dbFinanceSetup;
    private DbFinanceJournal dbFinanceJournal;
    private FinancePaymentPostingAdapter adapter;

    @BeforeEach
    void setUp() {
        dbFinanceSetup = Mockito.mock(DbFinanceSetup.class);
        dbFinanceJournal = Mockito.mock(DbFinanceJournal.class);
        adapter = new FinancePaymentPostingAdapter(dbFinanceSetup, dbFinanceJournal, new ObjectMapper());

        stubMapping("payment.bank", BANK_ACCOUNT_ID);
        stubMapping("payment.cash_safe", CASH_SAFE_ACCOUNT_ID);
        stubMapping("payment.card_clearing", CARD_CLEARING_ACCOUNT_ID);
        stubMapping("payment.wallet_clearing", WALLET_CLEARING_ACCOUNT_ID);
        stubMapping("payment.cash_drawer", CASH_DRAWER_ACCOUNT_ID);
        stubMapping("payment.fee_expense", FEE_EXPENSE_ACCOUNT_ID);
        when(dbFinanceJournal.allocateSourceJournalNumber(COMPANY_ID, "payment.settlement", "PM-"))
                .thenReturn("PM-000001");
        when(dbFinanceJournal.createPostedSourceJournal(any())).thenReturn(JOURNAL_ID);
    }

    @Test
    void postCardSettlementDebitsBankAndFeeAndCreditsCardClearing() {
        UUID journalId = adapter.post(request("card_settlement", """
                {
                  "currencyCode": "EGP",
                  "settlementMethod": "card",
                  "destination": "bank",
                  "grossAmount": 1000.0000,
                  "feeAmount": 25.0000,
                  "netAmount": 975.0000,
                  "providerSettlementId": "SETTLE-CARD-1"
                }
                """));

        assertEquals(JOURNAL_ID, journalId);

        DbFinanceJournal.PostedSourceJournalCommand command = capturedCommand();
        assertEquals("payment", command.journalType());
        assertEquals("payment", command.sourceModule());
        assertEquals("card_settlement", command.sourceType());
        assertEquals("PAY-4001", command.sourceId());
        assertEquals("EGP", command.currencyCode());
        assertEquals(money("1000.0000"), command.totalDebit());
        assertEquals(money("1000.0000"), command.totalCredit());
        assertEquals(3, command.lines().size());
        assertLine(command.lines().get(0), BANK_ACCOUNT_ID, money("975.0000"), money("0.0000"), "SETTLE-CARD-1");
        assertLine(command.lines().get(1), FEE_EXPENSE_ACCOUNT_ID, money("25.0000"), money("0.0000"), "SETTLE-CARD-1");
        assertLine(command.lines().get(2), CARD_CLEARING_ACCOUNT_ID, money("0.0000"), money("1000.0000"), "SETTLE-CARD-1");
        verify(dbFinanceJournal).applyPostedJournalToAccountBalances(COMPANY_ID, JOURNAL_ID, 17);
    }

    @Test
    void postCashSafeDropDebitsSafeAndCreditsCashDrawer() {
        adapter.post(request("cash_safe_drop", """
                {
                  "currencyCode": "EGP",
                  "amount": 600.0000,
                  "paymentId": "SAFE-DROP-1"
                }
                """));

        DbFinanceJournal.PostedSourceJournalCommand command = capturedCommand();
        assertEquals("cash_safe_drop", command.sourceType());
        assertEquals(2, command.lines().size());
        assertLine(command.lines().get(0), CASH_SAFE_ACCOUNT_ID, money("600.0000"), money("0.0000"), "SAFE-DROP-1");
        assertLine(command.lines().get(1), CASH_DRAWER_ACCOUNT_ID, money("0.0000"), money("600.0000"), "SAFE-DROP-1");
    }

    @Test
    void postWalletSettlementInfersWalletClearingAndBankDestination() {
        adapter.post(request("wallet_settlement", """
                {
                  "settledAmount": 490.0000,
                  "providerFeeAmount": 10.0000,
                  "providerReference": "WALLET-SETTLE-1"
                }
                """));

        DbFinanceJournal.PostedSourceJournalCommand command = capturedCommand();
        assertEquals(money("500.0000"), command.totalDebit());
        assertEquals(money("500.0000"), command.totalCredit());
        assertLine(command.lines().get(0), BANK_ACCOUNT_ID, money("490.0000"), money("0.0000"), "WALLET-SETTLE-1");
        assertLine(command.lines().get(1), FEE_EXPENSE_ACCOUNT_ID, money("10.0000"), money("0.0000"), "WALLET-SETTLE-1");
        assertLine(command.lines().get(2), WALLET_CLEARING_ACCOUNT_ID, money("0.0000"), money("500.0000"), "WALLET-SETTLE-1");
    }

    @Test
    void postRejectsFeeGreaterThanGross() {
        ApiException exception = assertThrows(ApiException.class, () -> adapter.post(request("card_settlement", """
                {
                  "grossAmount": 100.0000,
                  "feeAmount": 110.0000,
                  "netAmount": 10.0000
                }
                """)));

        assertEquals("FINANCE_PAYMENT_FEE_INVALID", exception.getCode());
    }

    @Test
    void postRejectsGrossThatDoesNotEqualNetPlusFee() {
        ApiException exception = assertThrows(ApiException.class, () -> adapter.post(request("card_settlement", """
                {
                  "grossAmount": 100.0000,
                  "feeAmount": 10.0000,
                  "netAmount": 80.0000
                }
                """)));

        assertEquals("FINANCE_PAYMENT_TOTAL_MISMATCH", exception.getCode());
    }

    @Test
    void postRejectsMissingMapping() {
        when(dbFinanceSetup.resolveActiveAccountMapping(COMPANY_ID, BRANCH_ID, "payment.card_clearing",
                LocalDate.of(2026, 5, 9))).thenReturn(null);

        ApiException exception = assertThrows(ApiException.class, () -> adapter.post(request("card_settlement", """
                {
                  "grossAmount": 100.0000,
                  "netAmount": 100.0000
                }
                """)));

        assertEquals("FINANCE_ACCOUNT_MAPPING_MISSING", exception.getCode());
    }

    private DbFinanceJournal.PostedSourceJournalCommand capturedCommand() {
        ArgumentCaptor<DbFinanceJournal.PostedSourceJournalCommand> commandCaptor =
                ArgumentCaptor.forClass(DbFinanceJournal.PostedSourceJournalCommand.class);
        verify(dbFinanceJournal).createPostedSourceJournal(commandCaptor.capture());
        return commandCaptor.getValue();
    }

    private void assertLine(DbFinanceJournal.PostedSourceJournalLineCommand line,
                            UUID accountId,
                            BigDecimal debit,
                            BigDecimal credit,
                            String paymentId) {
        assertEquals(accountId, line.accountId());
        assertEquals(BRANCH_ID, line.branchId());
        assertEquals(debit, line.debitAmount());
        assertEquals(credit, line.creditAmount());
        assertEquals(paymentId, line.paymentId());
    }

    private void stubMapping(String mappingKey, UUID accountId) {
        when(dbFinanceSetup.resolveActiveAccountMapping(COMPANY_ID, BRANCH_ID, mappingKey,
                LocalDate.of(2026, 5, 9))).thenReturn(mapping(mappingKey, accountId));
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

    private FinancePostingRequestItem request(String sourceType, String payload) {
        return new FinancePostingRequestItem(
                UUID.fromString("00000000-0000-0000-0000-000000004201"),
                COMPANY_ID,
                BRANCH_ID,
                null,
                "payment",
                sourceType,
                "PAY-4001",
                LocalDate.of(2026, 5, 9),
                FISCAL_PERIOD_ID,
                "hash-4",
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
