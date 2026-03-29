/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.Controller.posController;

import com.example.valueinsoftbackend.Model.InventoryTransaction;
import com.example.valueinsoftbackend.Model.Request.CreateInventoryTransactionRequest;
import com.example.valueinsoftbackend.Model.Request.InventoryTransactionQueryRequest;
import com.example.valueinsoftbackend.Service.InventoryTransactionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@Validated
@RequestMapping("/invTrans")
public class InventoryTransactionController {

    private final InventoryTransactionService inventoryTransactionService;

    public InventoryTransactionController(InventoryTransactionService inventoryTransactionService) {
        this.inventoryTransactionService = inventoryTransactionService;
    }

    @PostMapping("/AddTransaction")
    public ResponseEntity<Object> newTransaction(@Valid @RequestBody CreateInventoryTransactionRequest body) {
        inventoryTransactionService.addTransaction(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(successMessage(body.getBranchId()));
    }

    @PostMapping("/transactions")
    public ResponseEntity<Object> newUser(@Valid @RequestBody InventoryTransactionQueryRequest body) {
        List<InventoryTransaction> inventoryTransactions = inventoryTransactionService.getTransactions(body);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(inventoryTransactions);
    }

    private String successMessage(int branchId) {
        return "{\"title\" : \"the newTransaction added \", \"branchId\" : " + branchId + "}";
    }
}
