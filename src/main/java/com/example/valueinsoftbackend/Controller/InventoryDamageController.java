package com.example.valueinsoftbackend.Controller;

import com.example.valueinsoftbackend.Model.Request.Inventory.InventoryDamageRequest;
import com.example.valueinsoftbackend.Model.Request.Inventory.InventoryDamageReversalRequest;
import com.example.valueinsoftbackend.Model.Response.Inventory.InventoryDamageResponse;
import com.example.valueinsoftbackend.Service.inventory.InventoryDamageService;
import com.example.valueinsoftbackend.Service.security.AuthorizationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@Validated
@RequestMapping("/api/inventory/damages")
public class InventoryDamageController {

    private final InventoryDamageService damageService;
    private final AuthorizationService authorizationService;

    public InventoryDamageController(InventoryDamageService damageService,
                                     AuthorizationService authorizationService) {
        this.damageService = damageService;
        this.authorizationService = authorizationService;
    }

    @PostMapping
    public ResponseEntity<InventoryDamageResponse> create(
            @Valid @RequestBody InventoryDamageRequest request,
            Principal principal) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(), request.companyId(), request.branchId(), "inventory.adjustment.create");
        InventoryDamageResponse response = damageService.create(principal.getName(), request);
        return ResponseEntity.status(response.idempotentReplay() ? HttpStatus.OK : HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{damageId}/reversal")
    public ResponseEntity<InventoryDamageResponse> reverse(
            @PathVariable @Positive long damageId,
            @Valid @RequestBody InventoryDamageReversalRequest request,
            Principal principal) {
        if (request.damageId() != damageId) {
            throw new com.example.valueinsoftbackend.ExceptionPack.ApiException(
                    HttpStatus.BAD_REQUEST, "DAMAGE_ID_MISMATCH", "damageId in the body does not match the path");
        }
        authorizationService.assertAuthenticatedCapability(
                principal.getName(), request.companyId(), request.branchId(), "inventory.adjustment.edit");
        InventoryDamageResponse response = damageService.reverse(principal.getName(), request);
        return ResponseEntity.status(response.idempotentReplay() ? HttpStatus.OK : HttpStatus.CREATED).body(response);
    }
}
