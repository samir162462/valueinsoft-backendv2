package com.example.valueinsoftbackend.Service;

import lombok.extern.slf4j.Slf4j;

import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbExpenses;
import com.example.valueinsoftbackend.Model.Expenses;
import com.example.valueinsoftbackend.Model.Request.ExpenseUpsertRequest;
import com.example.valueinsoftbackend.Model.Sales.ExpensesSum;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
public class ExpensesService {

    private final DbExpenses dbExpenses;

    public ExpensesService(DbExpenses dbExpenses) {
        this.dbExpenses = dbExpenses;
    }

    public List<Expenses> getAllStaticExpenses(int companyId, int branchId) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        return dbExpenses.getAllExpensesItems(branchId, companyId, true);
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
        String result = dbExpenses.updateExpenses(branchId, companyId, toExpenses(request, branchId));
        log.info("Updated expense {} for company {} branch {}", request.getExId(), companyId, branchId);
        return result;
    }

    private Expenses toExpenses(ExpenseUpsertRequest request, int branchId) {
        return new Expenses(
                request.getExId(),
                request.getType().trim(),
                request.getAmount(),
                request.getTime(),
                branchId,
                request.getUser().trim(),
                request.getName().trim(),
                request.getPeriod()
        );
    }
}
