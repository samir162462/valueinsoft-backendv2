package com.example.valueinsoftbackend.Controller.MoneyController;

import com.example.valueinsoftbackend.Controller.Intefaces.Crud;
import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbExpenses;
import com.example.valueinsoftbackend.Model.Expenses;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ExpensesStatic")
public class ExpensesStaticController implements Crud {

    private final DbExpenses dbExpenses;
    private final ObjectMapper objectMapper;

    public ExpensesStaticController(DbExpenses dbExpenses, ObjectMapper objectMapper) {
        this.dbExpenses = dbExpenses;
        this.objectMapper = objectMapper;
    }

    @Override
    public ResponseEntity<Object> getAll(int companyId, int branchId, String option) {
        return ResponseEntity.ok(dbExpenses.getAllExpensesItems(branchId, companyId, true));
    }

    @Override
    public ResponseEntity<Object> getById(int companyId, int branchId, int oId) {
        return null;
    }

    @Override
    public ResponseEntity<Object> create(Object body, int companyId, int branchId) {
        Expenses expenses = objectMapper.convertValue(body, Expenses.class);
        return ResponseEntity.status(HttpStatus.CREATED).body(dbExpenses.addExpenses(branchId, companyId, expenses, true));
    }

    @Override
    public ResponseEntity<Object> updateById(Object body, int companyId, int branchId) {
        return null;
    }

    @Override
    public ResponseEntity<Object> DeleteById(int companyId, int branchId) {
        return null;
    }
}
