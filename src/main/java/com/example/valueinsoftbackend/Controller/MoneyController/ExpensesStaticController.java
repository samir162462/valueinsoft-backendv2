package com.example.valueinsoftbackend.Controller.MoneyController;

import com.example.valueinsoftbackend.Model.Request.ExpenseUpsertRequest;
import com.example.valueinsoftbackend.Service.ExpensesService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Positive;

@RestController
@Validated
@RequestMapping("/ExpensesStatic")
public class ExpensesStaticController {

    private final ExpensesService expensesService;

    public ExpensesStaticController(ExpensesService expensesService) {
        this.expensesService = expensesService;
    }

    @GetMapping("{companyId}/{branchId}/{option}/getAll")
    public ResponseEntity<Object> getAll(@PathVariable @Positive int companyId,
                                         @PathVariable @Positive int branchId,
                                         @PathVariable @NotBlank String option) {
        return ResponseEntity.ok(expensesService.getAllStaticExpenses(companyId, branchId));
    }

    @PostMapping("{companyId}/{branchId}/create")
    public ResponseEntity<Object> create(@PathVariable @Positive int companyId,
                                         @PathVariable @Positive int branchId,
                                         @Valid @RequestBody ExpenseUpsertRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(expensesService.createExpense(companyId, branchId, body, true));
    }
}
