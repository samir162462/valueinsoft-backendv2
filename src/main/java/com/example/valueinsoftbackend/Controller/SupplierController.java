/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.Controller;

import com.example.valueinsoftbackend.Model.InventoryTransaction;
import com.example.valueinsoftbackend.Model.Request.SupplierCreateRequest;
import com.example.valueinsoftbackend.Model.Request.SupplierProductCreateRequest;
import com.example.valueinsoftbackend.Model.Request.SupplierUpdateRequest;
import com.example.valueinsoftbackend.Model.Supplier;
import com.example.valueinsoftbackend.Model.SupplierBProduct;
import com.example.valueinsoftbackend.Service.SupplierService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.Positive;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@Validated
@RequestMapping("/suppliers")
public class SupplierController {

    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @GetMapping("/all/{companyId}/{branchId}")
    public List<Supplier> getsuppliers(
            @PathVariable @Positive int companyId,
            @PathVariable @Positive int branchId
    ) {
        return supplierService.getSuppliers(companyId, branchId);
    }

    @GetMapping({"{companyId}{branchId}/remain/{productId}", "{companyId}/{branchId}/remain/{productId}"})
    public String getRemaining(
            @PathVariable @Positive int productId,
            @PathVariable @Positive int companyId,
            @PathVariable @Positive int branchId
    ) {
        return supplierService.getRemainingAmountByProductId(companyId, branchId, productId).toString();
    }

    @PostMapping("/saveSupplier")
    public ResponseEntity<Object> newSupplier(@Valid @RequestBody SupplierCreateRequest requestBody) {
        String answer = supplierService.createSupplier(requestBody);
        return ResponseEntity.status(HttpStatus.CREATED).body(answer);
    }

    @PutMapping("{companyId}/{branchId}/update/{id}")
    public Map<String, String> updateSupplier(
            @Valid @RequestBody SupplierUpdateRequest supplier,
            @PathVariable @Positive int branchId,
            @PathVariable @Positive int companyId,
            @PathVariable("id") @Positive int supplierId
    ) {
        Map<String, String> response = new HashMap<>();
        response.put("Response", supplierService.updateSupplier(companyId, branchId, supplierId, supplier));
        return response;
    }

    @DeleteMapping("{companyId}/{branchId}/delete/{id}")
    public Map<String, Boolean> deleteUser(
            @PathVariable(value = "id") @Positive int supplierId,
            @PathVariable @Positive int branchId,
            @PathVariable @Positive int companyId
    ) {
        Map<String, Boolean> response = new HashMap<>();
        response.put("deleted", supplierService.deleteSupplier(companyId, branchId, supplierId));
        return response;
    }

    @GetMapping("{companyId}/{branchId}/SupplierSales/{supplierId}")
    public List<InventoryTransaction> getSupplierSales(
            @PathVariable @Positive int supplierId,
            @PathVariable @Positive int companyId,
            @PathVariable @Positive int branchId
    ) {
        return supplierService.getSupplierSales(companyId, branchId, supplierId);
    }

    @GetMapping("{companyId}/{branchId}/SupplierBProduct/{supplierId}")
    public List<SupplierBProduct> getSupplierBProduct(
            @PathVariable @Positive int supplierId,
            @PathVariable @Positive int companyId,
            @PathVariable @Positive int branchId
    ) {
        return supplierService.getSupplierBoughtProducts(companyId, branchId, supplierId);
    }

    @PostMapping("{companyId}/{branchId}/saveSupplierBProduct/{productId}")
    public ResponseEntity<Object> newSupplierBProduct(
            @PathVariable @Positive int productId,
            @PathVariable @Positive int companyId,
            @PathVariable @Positive int branchId,
            @Valid @RequestBody SupplierProductCreateRequest requestBody
    ) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(supplierService.createSupplierBoughtProduct(companyId, branchId, productId, requestBody));
    }
}
