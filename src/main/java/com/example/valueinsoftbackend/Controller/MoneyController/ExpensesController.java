package com.example.valueinsoftbackend.Controller.MoneyController;

import com.example.valueinsoftbackend.Model.Request.ExpenseUpsertRequest;
import com.example.valueinsoftbackend.Service.AuthorizationService;
import com.example.valueinsoftbackend.Service.ExpensesService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Positive;
import java.security.Principal;

@RestController
@Validated
@RequestMapping("/Expenses")
public class ExpensesController {

    private final ExpensesService expensesService;
    private final AuthorizationService authorizationService;

    public ExpensesController(ExpensesService expensesService, AuthorizationService authorizationService) {
        this.expensesService = expensesService;
        this.authorizationService = authorizationService;
    }

    @GetMapping("{companyId}/{branchId}/{option}/getAll")
    public ResponseEntity<Object> getAll(@PathVariable @Positive int companyId,
                                         @PathVariable @Positive int branchId,
                                         @PathVariable @NotBlank String option,
                                         Principal principal) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                branchId,
                "finance.entry.read"
        );
        return ResponseEntity.ok(expensesService.getPurchasesExpensesByMonth(companyId, branchId, option));
    }

    @PostMapping("{companyId}/{branchId}/create")
    public ResponseEntity<Object> create(@PathVariable @Positive int companyId,
                                         @PathVariable @Positive int branchId,
                                         @Valid @RequestBody ExpenseUpsertRequest body,
                                         Principal principal) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                branchId,
                "finance.entry.create"
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(expensesService.createExpense(companyId, branchId, body, false));
    }

    @PutMapping("{companyId}/{branchId}/update")
    public ResponseEntity<Object> updateById(@PathVariable @Positive int companyId,
                                             @PathVariable @Positive int branchId,
                                             @Valid @RequestBody ExpenseUpsertRequest body,
                                             Principal principal) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                branchId,
                "finance.entry.edit"
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(expensesService.updateExpense(companyId, branchId, body));
    }
}
