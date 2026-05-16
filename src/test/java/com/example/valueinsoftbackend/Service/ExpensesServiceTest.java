package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbExpenses;
import com.example.valueinsoftbackend.Model.Expenses;
import com.example.valueinsoftbackend.Model.ExpensesStatic;
import com.example.valueinsoftbackend.Model.ExpensesStaticHistory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExpensesServiceTest {

    private static final int COMPANY_ID = 1095;
    private static final int BRANCH_ID = 1074;
    private static final int STATIC_EXPENSE_ID = 4;
    private static final int GENERATED_EXPENSE_ID = 88;

    private DbExpenses dbExpenses;
    private FinanceOperationalPostingService financeOperationalPostingService;
    private FinanceAuditService financeAuditService;
    private ExpensesService service;

    @BeforeEach
    void setUp() {
        dbExpenses = Mockito.mock(DbExpenses.class);
        financeOperationalPostingService = Mockito.mock(FinanceOperationalPostingService.class);
        financeAuditService = Mockito.mock(FinanceAuditService.class);
        service = new ExpensesService(dbExpenses, financeOperationalPostingService, financeAuditService);
    }

    @Test
    void postStaticExpenseUsesGeneratedOperationalExpenseIdForPostingAndHistory() {
        UUID expenseAccountId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UUID paymentAccountId = UUID.fromString("00000000-0000-0000-0000-000000000102");
        ExpensesStatic template = new ExpensesStatic(
                STATIC_EXPENSE_ID,
                "Static",
                BigDecimal.valueOf(300),
                Timestamp.valueOf("2026-05-16 00:00:00"),
                BRANCH_ID,
                "sam",
                "Internet",
                "Monthly",
                expenseAccountId,
                paymentAccountId,
                null,
                null,
                LocalDate.of(2026, 5, 16),
                false);
        when(dbExpenses.getStaticExpenseById(COMPANY_ID, BRANCH_ID, STATIC_EXPENSE_ID)).thenReturn(template);
        when(dbExpenses.addOperationalExpenseAndReturnId(
                org.mockito.ArgumentMatchers.eq(BRANCH_ID),
                org.mockito.ArgumentMatchers.eq(COMPANY_ID),
                org.mockito.ArgumentMatchers.any(Expenses.class)))
                .thenReturn(GENERATED_EXPENSE_ID);
        when(financeAuditService.resolveActorUserId("sam")).thenReturn(17);

        service.postExpense(COMPANY_ID, BRANCH_ID, STATIC_EXPENSE_ID, "sam", true);

        ArgumentCaptor<Expenses> expenseCaptor = ArgumentCaptor.forClass(Expenses.class);
        verify(dbExpenses).addOperationalExpenseAndReturnId(
                org.mockito.ArgumentMatchers.eq(BRANCH_ID),
                org.mockito.ArgumentMatchers.eq(COMPANY_ID),
                expenseCaptor.capture());
        assertEquals("Internet (2026-05-16)", expenseCaptor.getValue().getName());

        ArgumentCaptor<Expenses> postedExpenseCaptor = ArgumentCaptor.forClass(Expenses.class);
        verify(financeOperationalPostingService).enqueueExpense(
                org.mockito.ArgumentMatchers.eq(COMPANY_ID),
                org.mockito.ArgumentMatchers.eq(BRANCH_ID),
                postedExpenseCaptor.capture(),
                org.mockito.ArgumentMatchers.eq("sam"));
        assertEquals(GENERATED_EXPENSE_ID, postedExpenseCaptor.getValue().getEId());
        assertEquals("Internet (2026-05-16)", postedExpenseCaptor.getValue().getName());

        ArgumentCaptor<ExpensesStaticHistory> historyCaptor = ArgumentCaptor.forClass(ExpensesStaticHistory.class);
        verify(dbExpenses).recordStaticExpenseHistory(
                org.mockito.ArgumentMatchers.eq(COMPANY_ID),
                historyCaptor.capture());
        assertEquals(GENERATED_EXPENSE_ID, historyCaptor.getValue().getExpenseId());
        assertEquals(LocalDate.of(2026, 5, 16), historyCaptor.getValue().getDueDate());
    }
}
