package com.example.valueinsoftbackend.Controller;

import com.example.valueinsoftbackend.Model.Request.Inventory.InventoryAdjustmentRequest;
import com.example.valueinsoftbackend.Model.Response.Inventory.InventoryAdjustmentResponse;
import com.example.valueinsoftbackend.Service.inventory.InventoryAdjustmentService;
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
@RequestMapping("/api/inventory/adjustments")
public class InventoryAdjustmentController {

    private final InventoryAdjustmentService adjustmentService;
    private final AuthorizationService authorizationService;

    public InventoryAdjustmentController(InventoryAdjustmentService adjustmentService,
                                         AuthorizationService authorizationService) {
        this.adjustmentService = adjustmentService;
        this.authorizationService = authorizationService;
    }

    @PostMapping
    public ResponseEntity<InventoryAdjustmentResponse> adjust(
            @Valid @RequestBody InventoryAdjustmentRequest request,
            Principal principal) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                request.companyId(),
                request.branchId(),
                "inventory.adjustment.create"
        );
        InventoryAdjustmentResponse response = adjustmentService.adjust(principal.getName(), request);
        return ResponseEntity.status(response.idempotentReplay() ? HttpStatus.OK : HttpStatus.CREATED).body(response);
    }
}
