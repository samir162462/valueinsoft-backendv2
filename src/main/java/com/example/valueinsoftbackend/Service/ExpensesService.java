package com.example.valueinsoftbackend.Service;

import lombok.extern.slf4j.Slf4j;

import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbExpenses;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Expenses;
import com.example.valueinsoftbackend.Model.Request.ExpenseUpsertRequest;
import com.example.valueinsoftbackend.Model.Sales.ExpensesSum;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
public class ExpensesService {

    private final DbExpenses dbExpenses;
    private final FinanceOperationalPostingService financeOperationalPostingService;
    private final FinanceAuditService financeAuditService;

    public ExpensesService(DbExpenses dbExpenses,
                           FinanceOperationalPostingService financeOperationalPostingService,
                           FinanceAuditService financeAuditService) {
        this.dbExpenses = dbExpenses;
        this.financeOperationalPostingService = financeOperationalPostingService;
        this.financeAuditService = financeAuditService;
    }

    public List<com.example.valueinsoftbackend.Model.ExpensesStatic> getAllStaticExpenses(int companyId, int branchId) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        return dbExpenses.getAllStaticExpenses(branchId, companyId);
    }

    public List<ExpensesSum> getPurchasesExpensesByMonth(int companyId, int branchId, String option) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        return dbExpenses.getPurchasesExpensesByMonth(branchId, companyId, option);
    }

    @Transactional
    public String createExpense(int companyId, int branchId, ExpenseUpsertRequest request, boolean isStatic) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        String result = dbExpenses.addExpenses(branchId, companyId, toExpenses(request, branchId), isStatic);
        log.info("Created {}expense entry for company {} branch {}", isStatic ? "static " : "", companyId, branchId);
        return result;
    }

    @Transactional
    public String updateExpense(int companyId, int branchId, ExpenseUpsertRequest request) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        String result = dbExpenses.updateExpenses(branchId, companyId, toExpenses(request, branchId), request.isStatic());
        log.info("Updated {}expense {} for company {} branch {}", request.isStatic() ? "static " : "", request.getEId(), companyId, branchId);
        return result;
    }

    @Transactional
    public String postExpense(int companyId, int branchId, int eId, String actorName, boolean isStatic) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        
        if (!isStatic) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ONLY_STATIC_SUPPORTED", "Only static expenses can be posted via this smart recurrence path");
        }

        com.example.valueinsoftbackend.Model.ExpensesStatic template = dbExpenses.getStaticExpenseById(companyId, branchId, eId);
        if (template == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "EXPENSE_NOT_FOUND", "Static expense template not found");
        }

        if (template.getExpenseAccountId() == null || template.getPaymentAccountId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EXPENSE_ACCOUNTS_MISSING", "Both expense and payment accounts must be set on the template before posting");
        }

        java.time.LocalDate dueDate = template.getNextDueDate();
        if (dueDate == null) {
            // Default to today if not set
            dueDate = java.time.LocalDate.now();
        }

        // 1. Spawn actual Expense record
        Expenses instance = new Expenses(
                0,
                template.getType(),
                template.getAmount(),
                java.sql.Timestamp.valueOf(dueDate.atStartOfDay()),
                branchId,
                template.getUser(),
                template.getName() + " (" + dueDate + ")",
                template.getPeriod(),
                template.getExpenseAccountId(),
                template.getPaymentAccountId(),
                null, // postedJournalEntryId
                null  // nextDueDate (instance is not recurring)
        );
        
        // We use the same addExpenses method but with isStatic = false
        dbExpenses.addExpenses(branchId, companyId, instance, false);
        
        // Find the created instance to get its eId (optional, but good for history)
        // For simplicity, we'll assume it worked and move on to posting
        
        // 2. Enqueue for Finance Posting
        financeOperationalPostingService.enqueueExpense(companyId, branchId, instance, actorName);

        // 3. Advance Next Due Date
        java.time.LocalDate lastPostedDate = java.time.LocalDate.now();
        java.time.LocalDate nextDueDate = calculateNextDueDate(dueDate, template.getPeriod());
        dbExpenses.updateStaticExpenseRecurrence(companyId, eId, lastPostedDate, nextDueDate);

        // 4. Record History
        com.example.valueinsoftbackend.Model.ExpensesStaticHistory history = new com.example.valueinsoftbackend.Model.ExpensesStaticHistory(
                0,
                companyId,
                eId,
                dueDate,
                lastPostedDate,
                template.getAmount(),
                "posted",
                null, // Could link to instance eId if we had it easily
                null, // Journal entry ID will be set by the posting adapter later
                new java.sql.Timestamp(System.currentTimeMillis()),
                financeAuditService.resolveActorUserId(actorName)
        );
        dbExpenses.recordStaticExpenseHistory(companyId, history);

        return "Expense posted for " + dueDate + ". Next due: " + nextDueDate;
    }

    public List<com.example.valueinsoftbackend.Model.ExpensesStaticHistory> getStaticExpenseHistory(int companyId, int branchId, int eId) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        return dbExpenses.getStaticExpenseHistory(companyId, eId);
    }

    private java.time.LocalDate calculateNextDueDate(java.time.LocalDate currentDue, String period) {
        if (period == null) return currentDue.plusMonths(1);
        
        switch (period.toLowerCase()) {
            case "daily": return currentDue.plusDays(1);
            case "weekly": return currentDue.plusWeeks(1);
            case "monthly": return currentDue.plusMonths(1);
            case "quarterly": return currentDue.plusMonths(3);
            case "yearly": return currentDue.plusYears(1);
            default: return currentDue.plusMonths(1);
        }
    }

    private Expenses toExpenses(ExpenseUpsertRequest request, int branchId) {
        return new Expenses(
                request.getEId(),
                request.getType().trim(),
                request.getAmount(),
                request.getTime(),
                branchId,
                request.getUser().trim(),
                request.getName().trim(),
                request.getPeriod(),
                request.getExpenseAccountId(),
                request.getPaymentAccountId(),
                null, // postedJournalEntryId
                request.getNextDueDate()
        );
    }
}
