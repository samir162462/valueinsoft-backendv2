/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.Controller.posController;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.DamagedItem;
import com.example.valueinsoftbackend.Model.Request.CreateDamagedItemRequest;
import com.example.valueinsoftbackend.Service.security.AuthorizationService;
import com.example.valueinsoftbackend.Service.DamagedItemService;
import com.example.valueinsoftbackend.Service.SupplierService;
import com.example.valueinsoftbackend.Service.inventory.InventoryLegacyWriterMetrics;
import com.example.valueinsoftbackend.Service.inventory.InventoryLegacyWriterGate;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.security.Principal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

@RestController
@Validated
@RequestMapping("/DamagedItem")
public class DamagedItemController {

    private final DamagedItemService damagedItemService;
    private final SupplierService supplierService;
    private final AuthorizationService authorizationService;
    private final InventoryLegacyWriterGate legacyWriterGate;
    private final InventoryLegacyWriterMetrics legacyWriterMetrics;

    @Autowired
    public DamagedItemController(DamagedItemService damagedItemService,
                                 SupplierService supplierService,
                                 AuthorizationService authorizationService,
                                 InventoryLegacyWriterGate legacyWriterGate,
                                 InventoryLegacyWriterMetrics legacyWriterMetrics) {
        this.damagedItemService = damagedItemService;
        this.supplierService = supplierService;
        this.authorizationService = authorizationService;
        this.legacyWriterGate = legacyWriterGate;
        this.legacyWriterMetrics = legacyWriterMetrics;
    }

    @RequestMapping(value = "{companyName}/{branchId}/all", method = RequestMethod.GET)
    @ResponseBody
    public List<DamagedItem> getDamagesItem(
            @PathVariable("companyName") @Positive int companyId,
            @PathVariable @Positive int branchId,
            Principal principal
    ) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                branchId,
                "inventory.item.read"
        );
        return damagedItemService.getDamagedItems(companyId, branchId);
    }

    @PostMapping("{companyName}/{branchId}/add")
    public ResponseEntity<Object> newDamagedItem(
            @Valid @RequestBody CreateDamagedItemRequest damagedItem,
            @PathVariable("companyName") @Positive int companyId,
            @PathVariable @Positive int branchId,
            Principal principal
    ) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                branchId,
                "inventory.adjustment.create"
        );
        throw new ApiException(
                HttpStatus.GONE,
                "TYPED_DAMAGE_COMMAND_REQUIRED",
                "Damage creation must use POST /api/inventory/damages");
    }

    @PutMapping("{companyName}/{branchId}/settle/{DId}")
    public Map<String, Boolean> settleDamagedItem(
            @PathVariable(value = "DId") @Positive int DId,
            @PathVariable("companyName") @Positive int companyId,
            @PathVariable @Positive int branchId,
            Principal principal
    ) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                branchId,
                "inventory.adjustment.edit"
        );
        Map<String, Boolean> response = new HashMap<>();
        boolean bol = damagedItemService.settleDamagedItem(companyId, branchId, DId);
        response.put("settled", bol);
        return response;
    }

    @DeleteMapping("{companyName}/{branchId}/delete/{DId}")
    public ResponseEntity<Map<String, Boolean>> deleteDamagedItem(
            @PathVariable(value = "DId") @Positive int DId,
            @PathVariable("companyName") @Positive int companyId,
            @PathVariable @Positive int branchId,
            Principal principal
    ) throws Exception {
        Timer.Sample sample = legacyWriterMetrics.start();
        String outcome = "success";
        try {
            authorizationService.assertAuthenticatedCapability(
                    principal.getName(),
                    companyId,
                    branchId,
                    "inventory.adjustment.edit"
            );
            legacyWriterGate.requireEnabled(
                    InventoryLegacyWriterGate.Writer.DAMAGE_HARD_DELETE,
                    companyId,
                    branchId
            );
            Map<String, Boolean> response = new HashMap<>();
            boolean bol = damagedItemService.deleteDamagedItem(companyId, branchId, DId);
            response.put("deleted", bol);
            return ResponseEntity.ok()
                    .header("Deprecation", "true")
                    .header("Warning", "299 ValueInSoft \"Deprecated inventory writer; use damage settlement or reversal\"")
                    .body(response);
        } catch (ApiException exception) {
            outcome = exception.getStatus() == HttpStatus.GONE ? "blocked" : "rejected";
            throw exception;
        } catch (RuntimeException exception) {
            outcome = "rejected";
            throw exception;
        } finally {
            legacyWriterMetrics.finish(sample, "damage_hard_delete", outcome);
        }
    }
}
