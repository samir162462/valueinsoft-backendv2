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

class FinanceInventoryPostingAdapterTest {

    private static final int COMPANY_ID = 7;
    private static final int BRANCH_ID = 3;
    private static final UUID FISCAL_PERIOD_ID = UUID.fromString("00000000-0000-0000-0000-000000003001");
    private static final UUID JOURNAL_ID = UUID.fromString("00000000-0000-0000-0000-000000003002");
    private static final UUID INVENTORY_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000003101");
    private static final UUID GAIN_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000003102");
    private static final UUID EXPENSE_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000003103");
    private static final UUID DAMAGE_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000003104");
    private static final UUID WRITEOFF_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000003105");
    private static final int DESTINATION_BRANCH_ID = 4;

    private DbFinanceSetup dbFinanceSetup;
    private DbFinanceJournal dbFinanceJournal;
    private FinanceInventoryPostingAdapter adapter;

    @BeforeEach
    void setUp() {
        dbFinanceSetup = Mockito.mock(DbFinanceSetup.class);
        dbFinanceJournal = Mockito.mock(DbFinanceJournal.class);
        adapter = new FinanceInventoryPostingAdapter(dbFinanceSetup, dbFinanceJournal, new ObjectMapper());

        stubMapping("inventory.asset", INVENTORY_ACCOUNT_ID);
        stubMapping("inventory.adjustment_gain", GAIN_ACCOUNT_ID);
        stubMapping("inventory.adjustment_expense", EXPENSE_ACCOUNT_ID);
        stubMapping("inventory.damage_expense", DAMAGE_ACCOUNT_ID);
        stubMapping("inventory.writeoff_expense", WRITEOFF_ACCOUNT_ID);
        stubMappingForBranch(DESTINATION_BRANCH_ID, "inventory.asset", INVENTORY_ACCOUNT_ID);
        when(dbFinanceJournal.allocateSourceJournalNumber(COMPANY_ID, "inventory.adjustment", "IA-"))
                .thenReturn("IA-000001");
        when(dbFinanceJournal.allocateSourceJournalNumber(COMPANY_ID, "inventory.transfer", "BT-"))
                .thenReturn("BT-000001");
        when(dbFinanceJournal.createPostedSourceJournal(any())).thenReturn(JOURNAL_ID);
    }

    @Test
    void postIncreaseDebitsInventoryAndCreditsAdjustmentGain() {
        UUID journalId = adapter.post(request("adjustment", """
                {
                  "currencyCode": "EGP",
                  "direction": "increase",
                  "reasonCode": "COUNT_GAIN",
                  "items": [
                    {"productId": 801, "quantity": 2, "unitCost": 25.0000, "inventoryMovementId": 4001},
                    {"productId": 802, "valuationAmount": 30.0000, "stockLedgerId": 4002}
                  ]
                }
                """));

        assertEquals(JOURNAL_ID, journalId);

        DbFinanceJournal.PostedSourceJournalCommand command = capturedCommand();
        assertEquals("inventory", command.journalType());
        assertEquals("inventory", command.sourceModule());
        assertEquals("adjustment", command.sourceType());
        assertEquals("INV-3001", command.sourceId());
        assertEquals(money("80.0000"), command.totalDebit());
        assertEquals(money("80.0000"), command.totalCredit());
        assertEquals(4, command.lines().size());
        assertLine(command.lines().get(0), INVENTORY_ACCOUNT_ID, money("50.0000"), money("0.0000"), 801L, 4001L);
        assertLine(command.lines().get(1), GAIN_ACCOUNT_ID, money("0.0000"), money("50.0000"), 801L, 4001L);
        assertLine(command.lines().get(2), INVENTORY_ACCOUNT_ID, money("30.0000"), money("0.0000"), 802L, 4002L);
        assertLine(command.lines().get(3), GAIN_ACCOUNT_ID, money("0.0000"), money("30.0000"), 802L, 4002L);
        verify(dbFinanceJournal).applyPostedJournalToAccountBalances(COMPANY_ID, JOURNAL_ID, 17);
    }

    @Test
    void postDecreaseDebitsAdjustmentExpenseAndCreditsInventory() {
        adapter.post(request("stock_adjustment", """
                {
                  "direction": "decrease",
                  "reason": "Cycle count loss",
                  "adjustmentAmount": 75.0000,
                  "productId": 803,
                  "inventoryMovementId": 4003
                }
                """));

        DbFinanceJournal.PostedSourceJournalCommand command = capturedCommand();
        assertEquals(money("75.0000"), command.totalDebit());
        assertEquals(money("75.0000"), command.totalCredit());
        assertEquals(2, command.lines().size());
        assertLine(command.lines().get(0), EXPENSE_ACCOUNT_ID, money("75.0000"), money("0.0000"), 803L, 4003L);
        assertLine(command.lines().get(1), INVENTORY_ACCOUNT_ID, money("0.0000"), money("75.0000"), 803L, 4003L);
    }

    @Test
    void postDamageUsesDamageExpenseMapping() {
        adapter.post(request("damage", """
                {
                  "reasonCode": "DAMAGED_SCREEN",
                  "valuationAmount": 45.0000,
                  "productId": 804,
                  "inventoryMovementId": 4004
                }
                """));

        DbFinanceJournal.PostedSourceJournalCommand command = capturedCommand();
        assertEquals("damage", command.sourceType());
        assertLine(command.lines().get(0), DAMAGE_ACCOUNT_ID, money("45.0000"), money("0.0000"), 804L, 4004L);
        assertLine(command.lines().get(1), INVENTORY_ACCOUNT_ID, money("0.0000"), money("45.0000"), 804L, 4004L);
    }

    @Test
    void postWriteOffUsesWriteoffExpenseMapping() {
        adapter.post(request("write_off", """
                {
                  "reasonCode": "OBSOLETE",
                  "amount": 90.0000,
                  "productId": 805,
                  "inventoryMovementId": 4005
                }
                """));

        DbFinanceJournal.PostedSourceJournalCommand command = capturedCommand();
        assertEquals("write_off", command.sourceType());
        assertLine(command.lines().get(0), WRITEOFF_ACCOUNT_ID, money("90.0000"), money("0.0000"), 805L, 4005L);
        assertLine(command.lines().get(1), INVENTORY_ACCOUNT_ID, money("0.0000"), money("90.0000"), 805L, 4005L);
    }

    @Test
    void postRejectsMissingReason() {
        ApiException exception = assertThrows(ApiException.class, () -> adapter.post(request("adjustment", """
                {
                  "direction": "increase",
                  "adjustmentAmount": 50.0000
                }
                """)));

        assertEquals("FINANCE_INVENTORY_REASON_REQUIRED", exception.getCode());
    }

    @Test
    void postRejectsMixedItemDirections() {
        ApiException exception = assertThrows(ApiException.class, () -> adapter.post(request("adjustment", """
                {
                  "reasonCode": "COUNT",
                  "items": [
                    {"direction": "increase", "valuationAmount": 20.0000},
                    {"direction": "decrease", "valuationAmount": 10.0000}
                  ]
                }
                """)));

        assertEquals("FINANCE_INVENTORY_DIRECTION_MISMATCH", exception.getCode());
    }

    @Test
    void postRejectsHeaderAmountMismatchAgainstItemValuation() {
        ApiException exception = assertThrows(ApiException.class, () -> adapter.post(request("adjustment", """
                {
                  "direction": "increase",
                  "reasonCode": "COUNT_GAIN",
                  "adjustmentAmount": 100.0000,
                  "items": [
                    {"productId": 801, "quantity": 2, "unitCost": 25.0000}
                  ]
                }
                """)));

        assertEquals("FINANCE_INVENTORY_TOTAL_MISMATCH", exception.getCode());
    }

    @Test
    void postStockTransferMovesInventoryBetweenBranchesWithoutPnlImpact() {
        adapter.post(request("stock_transfer", """
                {
                  "sourceBranchId": 3,
                  "destinationBranchId": 4,
                  "transferAmount": 80.0000,
                  "items": [
                    {
                      "productId": 901,
                      "quantity": 2,
                      "unitCost": 40.0000,
                      "sourceInventoryMovementId": 5001,
                      "destinationInventoryMovementId": 5002
                    }
                  ]
                }
                """));

        DbFinanceJournal.PostedSourceJournalCommand command = capturedCommand();
        assertEquals(null, command.branchId());
        assertEquals("BT-000001", command.journalNumber());
        assertEquals("inventory", command.journalType());
        assertEquals("stock_transfer", command.sourceType());
        assertEquals(money("80.0000"), command.totalDebit());
        assertEquals(money("80.0000"), command.totalCredit());
        assertEquals(2, command.lines().size());

        DbFinanceJournal.PostedSourceJournalLineCommand debitLine = command.lines().get(0);
        assertEquals(INVENTORY_ACCOUNT_ID, debitLine.accountId());
        assertEquals(DESTINATION_BRANCH_ID, debitLine.branchId());
        assertEquals(money("80.0000"), debitLine.debitAmount());
        assertEquals(money("0.0000"), debitLine.creditAmount());
        assertEquals(901L, debitLine.productId());
        assertEquals(5002L, debitLine.inventoryMovementId());

        DbFinanceJournal.PostedSourceJournalLineCommand creditLine = command.lines().get(1);
        assertEquals(INVENTORY_ACCOUNT_ID, creditLine.accountId());
        assertEquals(BRANCH_ID, creditLine.branchId());
        assertEquals(money("0.0000"), creditLine.debitAmount());
        assertEquals(money("80.0000"), creditLine.creditAmount());
        assertEquals(901L, creditLine.productId());
        assertEquals(5001L, creditLine.inventoryMovementId());
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
                            Long productId,
                            Long inventoryMovementId) {
        assertEquals(accountId, line.accountId());
        assertEquals(BRANCH_ID, line.branchId());
        assertEquals(debit, line.debitAmount());
        assertEquals(credit, line.creditAmount());
        assertEquals(productId, line.productId());
        assertEquals(inventoryMovementId, line.inventoryMovementId());
    }

    private void stubMapping(String mappingKey, UUID accountId) {
        stubMappingForBranch(BRANCH_ID, mappingKey, accountId);
    }

    private void stubMappingForBranch(Integer branchId, String mappingKey, UUID accountId) {
        when(dbFinanceSetup.resolveActiveAccountMapping(COMPANY_ID, branchId, mappingKey,
                LocalDate.of(2026, 4, 7))).thenReturn(mapping(branchId, mappingKey, accountId));
    }

    private FinanceAccountMappingItem mapping(Integer branchId, String mappingKey, UUID accountId) {
        return new FinanceAccountMappingItem(
                UUID.randomUUID(),
                COMPANY_ID,
                branchId,
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
                UUID.fromString("00000000-0000-0000-0000-000000003201"),
                COMPANY_ID,
                BRANCH_ID,
                null,
                "inventory",
                sourceType,
                "INV-3001",
                LocalDate.of(2026, 4, 7),
                FISCAL_PERIOD_ID,
                "hash-3",
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
