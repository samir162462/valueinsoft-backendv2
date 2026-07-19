package com.example.valueinsoftbackend.Controller;

import com.example.valueinsoftbackend.Model.Request.Inventory.InventorySupplierReturnRequest;
import com.example.valueinsoftbackend.Model.Response.Inventory.InventorySupplierReturnResponse;
import com.example.valueinsoftbackend.Service.inventory.InventorySupplierReturnService;
import com.example.valueinsoftbackend.Service.security.AuthorizationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@Validated
@RequestMapping("/api/inventory/supplier-returns")
public class InventorySupplierReturnController {

    private final InventorySupplierReturnService supplierReturnService;
    private final AuthorizationService authorizationService;

    public InventorySupplierReturnController(InventorySupplierReturnService supplierReturnService,
                                             AuthorizationService authorizationService) {
        this.supplierReturnService = supplierReturnService;
        this.authorizationService = authorizationService;
    }

    @PostMapping
    public ResponseEntity<InventorySupplierReturnResponse> create(
            @Valid @RequestBody InventorySupplierReturnRequest request,
            Principal principal) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(), request.companyId(), request.branchId(), "suppliers.return.create");
        InventorySupplierReturnResponse response = supplierReturnService.create(principal.getName(), request);
        return ResponseEntity.status(response.idempotentReplay() ? HttpStatus.OK : HttpStatus.CREATED).body(response);
    }
}
