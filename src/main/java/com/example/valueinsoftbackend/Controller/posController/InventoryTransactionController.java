/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.Controller.posController;

import com.example.valueinsoftbackend.Service.security.AuthorizationService;
import com.example.valueinsoftbackend.Service.security.TenantScopeGuard;
import com.example.valueinsoftbackend.Model.Inventory.ProductUnitStatus;
import com.example.valueinsoftbackend.Model.InventoryTransaction;
import com.example.valueinsoftbackend.Model.Request.CreateInventoryTransactionRequest;
import com.example.valueinsoftbackend.Model.Request.Inventory.SerializedUnitIdentifierUpdateRequest;
import com.example.valueinsoftbackend.Model.Request.Inventory.SerializedUnitStockInRequest;
import com.example.valueinsoftbackend.Model.Request.Inventory.SerializedUnitTransferRequest;
import com.example.valueinsoftbackend.Model.Request.InventoryTransactionQueryRequest;
import com.example.valueinsoftbackend.Service.SerializedInventoryService;
import com.example.valueinsoftbackend.Service.inventory.InventoryTransactionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;

@RestController
@Validated
@RequestMapping("/invTrans")
public class InventoryTransactionController {

    private final InventoryTransactionService inventoryTransactionService;
    private final SerializedInventoryService serializedInventoryService;
    private final AuthorizationService authorizationService;
    private final TenantScopeGuard tenantScopeGuard;

    public InventoryTransactionController(InventoryTransactionService inventoryTransactionService,
                                          SerializedInventoryService serializedInventoryService,
                                          AuthorizationService authorizationService,
                                          TenantScopeGuard tenantScopeGuard) {
        this.inventoryTransactionService = inventoryTransactionService;
        this.serializedInventoryService = serializedInventoryService;
        this.authorizationService = authorizationService;
        this.tenantScopeGuard = tenantScopeGuard;
    }

    @PostMapping("/AddTransaction")
    public ResponseEntity<Object> newTransaction(@Valid @RequestBody CreateInventoryTransactionRequest body,
                                                 Principal principal) {
        // P0-2: validate the request-supplied company/branch against the authenticated identity
        // before any capability check or service work. The service reads companyId from the body;
        // this guard guarantees that value belongs to the caller.
        tenantScopeGuard.requireScope(principal.getName(), body.getCompanyId(), body.getBranchId());
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                body.getCompanyId(),
                body.getBranchId(),
                "inventory.adjustment.create"
        );
        inventoryTransactionService.addTransaction(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(successMessage(body.getBranchId()));
    }

    @PostMapping("/AddSerializedStockIn")
    public ResponseEntity<Object> newSerializedStockIn(@Valid @RequestBody SerializedUnitStockInRequest body,
                                                       Principal principal) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                (int) body.getCompanyId(),
                (int) body.getBranchId(),
                "inventory.adjustment.create"
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(inventoryTransactionService.addSerializedStockIn(body));
    }

    @PostMapping("/TransferSerializedUnits")
    public ResponseEntity<Object> transferSerializedUnits(@Valid @RequestBody SerializedUnitTransferRequest body,
                                                         Principal principal) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                (int) body.getCompanyId(),
                (int) body.getFromBranchId(),
                "inventory.adjustment.create"
        );
        serializedInventoryService.transferSerializedUnits(body);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body("{\"title\" : \"serialized units transferred\"}");
    }

    @GetMapping("/SerializedScan/{companyId}/{branchId}/{scanCode}")
    public ResponseEntity<Object> scanSerializedUnit(@PathVariable("companyId") long companyId,
                                                     @PathVariable("branchId") long branchId,
                                                     @PathVariable("scanCode") String scanCode,
                                                     Principal principal) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                (int) companyId,
                (int) branchId,
                "inventory.item.read"
        );
        return ResponseEntity.ok(serializedInventoryService.scanUnit(companyId, branchId, scanCode));
    }

    @GetMapping("/SerializedUnits/{companyId}/{branchId}/{productId}")
    public ResponseEntity<Object> listSerializedUnits(@PathVariable("companyId") long companyId,
                                                      @PathVariable("branchId") long branchId,
                                                      @PathVariable("productId") long productId,
                                                      @RequestParam(value = "status", required = false) ProductUnitStatus status,
                                                      Principal principal) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                (int) companyId,
                (int) branchId,
                "inventory.item.read"
        );
        return ResponseEntity.ok(serializedInventoryService.listProductUnits(companyId, branchId, productId, status));
    }

    @PutMapping("/SerializedUnits/{companyId}/{branchId}/{productId}/{productUnitId}/identifier")
    public ResponseEntity<Object> updateSerializedUnitIdentifier(@PathVariable("companyId") long companyId,
                                                                 @PathVariable("branchId") long branchId,
                                                                 @PathVariable("productId") long productId,
                                                                 @PathVariable("productUnitId") long productUnitId,
                                                                 @Valid @RequestBody SerializedUnitIdentifierUpdateRequest body,
                                                                 Principal principal) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                (int) companyId,
                (int) branchId,
                "inventory.item.edit"
        );
        return ResponseEntity.ok(serializedInventoryService.updateSerializedUnitIdentifier(
                companyId,
                branchId,
                productId,
                productUnitId,
                body.getImei(),
                body.getSerialNumber(),
                body.getConditionCode()
        ));
    }

    @PostMapping("/SerializedUnits/{companyId}/{branchId}/{productUnitId}/condition-correction")
    public ResponseEntity<Object> correctSerializedUnitCondition(@PathVariable("companyId") long companyId,
                                                                 @PathVariable("branchId") long branchId,
                                                                 @PathVariable("productUnitId") long productUnitId,
                                                                 @Valid @RequestBody com.example.valueinsoftbackend.Model.Request.Inventory.SerializedUnitConditionCorrectionRequest body,
                                                                 Principal principal) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                (int) companyId,
                (int) branchId,
                "inventory.item.edit"
        );
        return ResponseEntity.ok(serializedInventoryService.correctSerializedUnitCondition(
                companyId,
                branchId,
                productUnitId,
                body.getConditionCode(),
                body.getReason(),
                principal.getName()
        ));
    }

    @GetMapping("/SerializedAvailability/{companyId}/{branchId}/{productId}")
    public ResponseEntity<Object> serializedAvailability(@PathVariable("companyId") long companyId,
                                                        @PathVariable("branchId") long branchId,
                                                        @PathVariable("productId") long productId,
                                                        Principal principal) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                (int) companyId,
                (int) branchId,
                "inventory.item.read"
        );
        return ResponseEntity.ok(serializedInventoryService.countAvailableSerializedUnits(companyId, branchId, productId));
    }

    @GetMapping("/StockMovements/{companyId}/{branchId}/{productId}")
    public ResponseEntity<Object> productStockMovements(@PathVariable("companyId") long companyId,
                                                        @PathVariable("branchId") long branchId,
                                                        @PathVariable("productId") long productId,
                                                        @RequestParam(value = "limit", defaultValue = "50") int limit,
                                                        Principal principal) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                (int) companyId,
                (int) branchId,
                "inventory.item.read"
        );
        return ResponseEntity.ok(serializedInventoryService.listProductMovementHistory(companyId, branchId, productId, limit));
    }

    @GetMapping("/SerializedUnitMovements/{companyId}/{branchId}/{productUnitId}")
    public ResponseEntity<Object> serializedUnitStockMovements(@PathVariable("companyId") long companyId,
                                                               @PathVariable("branchId") long branchId,
                                                               @PathVariable("productUnitId") long productUnitId,
                                                               @RequestParam(value = "limit", defaultValue = "50") int limit,
                                                               Principal principal) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                (int) companyId,
                (int) branchId,
                "inventory.item.read"
        );
        return ResponseEntity.ok(serializedInventoryService.listProductUnitMovementHistory(companyId, branchId, productUnitId, limit));
    }

    @PostMapping("/transactions")
    public ResponseEntity<Object> newUser(@Valid @RequestBody InventoryTransactionQueryRequest body,
                                          Principal principal) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                body.getCompanyId(),
                body.getBranchId(),
                "inventory.item.read"
        );
        List<InventoryTransaction> inventoryTransactions = inventoryTransactionService.getTransactions(body);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(inventoryTransactions);
    }

    private String successMessage(int branchId) {
        return "{\"title\" : \"the newTransaction added \", \"branchId\" : " + branchId + "}";
    }
}
