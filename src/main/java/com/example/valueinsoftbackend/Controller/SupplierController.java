/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.Controller;

import com.example.valueinsoftbackend.Model.InventoryTransaction;
import com.example.valueinsoftbackend.Model.Request.SupplierArchiveRequest;
import com.example.valueinsoftbackend.Model.Request.SupplierCreateRequest;
import com.example.valueinsoftbackend.Model.Request.SupplierProductCreateRequest;
import com.example.valueinsoftbackend.Model.Request.SupplierUpdateRequest;
import com.example.valueinsoftbackend.Model.Response.SupplierAgingResponse;
import com.example.valueinsoftbackend.Model.Response.SupplierAuditResponse;
import com.example.valueinsoftbackend.Model.Response.SupplierReferenceResponse;
import com.example.valueinsoftbackend.Model.Response.SupplierStatementResponse;
import com.example.valueinsoftbackend.Model.Supplier;
import com.example.valueinsoftbackend.Model.SupplierBProduct;
import com.example.valueinsoftbackend.Service.AuthorizationService;
import com.example.valueinsoftbackend.Service.SupplierService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.security.Principal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@Validated
@RequestMapping("/suppliers")
public class SupplierController {

    private final SupplierService supplierService;
    private final AuthorizationService authorizationService;

    public SupplierController(SupplierService supplierService, AuthorizationService authorizationService) {
        this.supplierService = supplierService;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/all/{companyId}/{branchId}")
    public List<Supplier> getsuppliers(
            @PathVariable @Positive int companyId,
            @PathVariable @Positive int branchId,
            Principal principal
    ) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                branchId,
                "suppliers.account.read"
        );
        return supplierService.getSuppliers(companyId, branchId);
    }

    @GetMapping({"{companyId}{branchId}/remain/{productId}", "{companyId}/{branchId}/remain/{productId}"})
    public String getRemaining(
            @PathVariable @Positive int productId,
            @PathVariable @Positive int companyId,
            @PathVariable @Positive int branchId,
            Principal principal
    ) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                branchId,
                "suppliers.account.read"
        );
        return supplierService.getRemainingAmountByProductId(companyId, branchId, productId).toString();
    }

    @GetMapping("{companyId}/{branchId}/{supplierId}")
    public Supplier getSupplier(
            @PathVariable @Positive int companyId,
            @PathVariable @Positive int branchId,
            @PathVariable @Positive int supplierId,
            Principal principal
    ) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                branchId,
                "suppliers.account.read"
        );
        return supplierService.getSupplier(companyId, branchId, supplierId);
    }

    @PostMapping("/saveSupplier")
    public ResponseEntity<Object> newSupplier(@Valid @RequestBody SupplierCreateRequest requestBody,
                                              Principal principal) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                requestBody.getCompanyId(),
                requestBody.getBranchId(),
                "suppliers.account.create"
        );
        String answer = supplierService.createSupplier(requestBody);
        return ResponseEntity.status(HttpStatus.CREATED).body(answer);
    }

    @PutMapping("{companyId}/{branchId}/update/{id}")
    public Map<String, String> updateSupplier(
            @Valid @RequestBody SupplierUpdateRequest supplier,
            @PathVariable @Positive int branchId,
            @PathVariable @Positive int companyId,
            @PathVariable("id") @Positive int supplierId,
            Principal principal
    ) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                branchId,
                "suppliers.account.edit"
        );
        Map<String, String> response = new HashMap<>();
        response.put("Response", supplierService.updateSupplier(companyId, branchId, supplierId, supplier));
        return response;
    }

    @DeleteMapping("{companyId}/{branchId}/delete/{id}")
    public Map<String, Boolean> deleteUser(
            @PathVariable(value = "id") @Positive int supplierId,
            @PathVariable @Positive int branchId,
            @PathVariable @Positive int companyId,
            Principal principal
    ) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                branchId,
                "suppliers.account.delete"
        );
        Map<String, Boolean> response = new HashMap<>();
        response.put("deleted", supplierService.deleteSupplier(companyId, branchId, supplierId));
        return response;
    }

    @GetMapping("{companyId}/{branchId}/{supplierId}/references")
    public SupplierReferenceResponse getSupplierReferences(
            @PathVariable @Positive int supplierId,
            @PathVariable @Positive int branchId,
            @PathVariable @Positive int companyId,
            Principal principal
    ) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                branchId,
                "suppliers.account.read"
        );
        return supplierService.getSupplierReferences(companyId, branchId, supplierId);
    }

    @PostMapping("{companyId}/{branchId}/{supplierId}/archive")
    public Supplier archiveSupplier(
            @PathVariable @Positive int supplierId,
            @PathVariable @Positive int branchId,
            @PathVariable @Positive int companyId,
            @Valid @RequestBody SupplierArchiveRequest request,
            Principal principal
    ) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                branchId,
                "suppliers.account.edit"
        );
        return supplierService.archiveSupplier(companyId, branchId, supplierId, request);
    }

    @PostMapping("{companyId}/{branchId}/{supplierId}/reactivate")
    public Supplier reactivateSupplier(
            @PathVariable @Positive int supplierId,
            @PathVariable @Positive int branchId,
            @PathVariable @Positive int companyId,
            Principal principal
    ) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                branchId,
                "suppliers.account.edit"
        );
        return supplierService.reactivateSupplier(companyId, branchId, supplierId);
    }

    @GetMapping("{companyId}/{branchId}/{supplierId}/statement")
    public SupplierStatementResponse getSupplierStatement(
            @PathVariable @Positive int supplierId,
            @PathVariable @Positive int branchId,
            @PathVariable @Positive int companyId,
            @RequestParam(value = "fromDate", required = false) LocalDate fromDate,
            @RequestParam(value = "toDate", required = false) LocalDate toDate,
            Principal principal
    ) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                branchId,
                "suppliers.account.read"
        );
        return supplierService.getSupplierStatement(companyId, branchId, supplierId, fromDate, toDate);
    }

    @GetMapping("{companyId}/{branchId}/{supplierId}/ap-aging")
    public SupplierAgingResponse getSupplierAging(
            @PathVariable @Positive int supplierId,
            @PathVariable @Positive int branchId,
            @PathVariable @Positive int companyId,
            @RequestParam(value = "asOfDate", required = false) LocalDate asOfDate,
            Principal principal
    ) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                branchId,
                "suppliers.account.read"
        );
        return supplierService.getSupplierAging(companyId, branchId, supplierId, asOfDate);
    }

    @GetMapping("{companyId}/{branchId}/{supplierId}/audit")
    public SupplierAuditResponse getSupplierAudit(
            @PathVariable @Positive int supplierId,
            @PathVariable @Positive int branchId,
            @PathVariable @Positive int companyId,
            @RequestParam(value = "fromDate", required = false) LocalDate fromDate,
            @RequestParam(value = "toDate", required = false) LocalDate toDate,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            Principal principal
    ) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                branchId,
                "suppliers.account.read"
        );
        return supplierService.getSupplierAudit(companyId, branchId, supplierId, fromDate, toDate, page, size);
    }

    @GetMapping("{companyId}/{branchId}/SupplierSales/{supplierId}")
    public List<InventoryTransaction> getSupplierSales(
            @PathVariable @Positive int supplierId,
            @PathVariable @Positive int companyId,
            @PathVariable @Positive int branchId,
            Principal principal
    ) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                branchId,
                "suppliers.account.read"
        );
        return supplierService.getSupplierSales(companyId, branchId, supplierId);
    }

    @GetMapping("{companyId}/{branchId}/SupplierBProduct/{supplierId}")
    public List<SupplierBProduct> getSupplierBProduct(
            @PathVariable @Positive int supplierId,
            @PathVariable @Positive int companyId,
            @PathVariable @Positive int branchId,
            Principal principal
    ) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                branchId,
                "suppliers.account.read"
        );
        return supplierService.getSupplierBoughtProducts(companyId, branchId, supplierId);
    }

    @PostMapping("{companyId}/{branchId}/saveSupplierBProduct/{productId}")
    public ResponseEntity<Object> newSupplierBProduct(
            @PathVariable @Positive int productId,
            @PathVariable @Positive int companyId,
            @PathVariable @Positive int branchId,
            @Valid @RequestBody SupplierProductCreateRequest requestBody,
            Principal principal
    ) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                branchId,
                "suppliers.account.edit"
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(supplierService.createSupplierBoughtProduct(companyId, branchId, productId, requestBody));
    }
}
