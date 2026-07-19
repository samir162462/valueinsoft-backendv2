package com.example.valueinsoftbackend.Controller;

import com.example.valueinsoftbackend.Model.Inventory.InventoryReconciliationSnapshot;
import com.example.valueinsoftbackend.Service.inventory.InventoryReconciliationService;
import com.example.valueinsoftbackend.Service.security.AuthorizationService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@Validated
@RequestMapping("/api/inventory/reconciliation")
public class InventoryReconciliationController {

    private final InventoryReconciliationService reconciliationService;
    private final AuthorizationService authorizationService;

    public InventoryReconciliationController(InventoryReconciliationService reconciliationService,
                                             AuthorizationService authorizationService) {
        this.reconciliationService = reconciliationService;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/{companyId}/{branchId}")
    public InventoryReconciliationSnapshot snapshot(
            @PathVariable @Positive int companyId,
            @PathVariable @Positive int branchId,
            @RequestParam(defaultValue = "200") @Min(1) @Max(1000) int limit,
            Principal principal) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                branchId,
                "inventory.item.read"
        );
        return reconciliationService.snapshot(companyId, branchId, limit);
    }
}
