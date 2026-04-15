/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.Controller.posController;

import com.example.valueinsoftbackend.Model.Request.FixAreaSlotCreateRequest;
import com.example.valueinsoftbackend.Model.Request.FixAreaSlotUpdateRequest;
import com.example.valueinsoftbackend.Model.Slots.SlotsFixArea;
import com.example.valueinsoftbackend.Service.AuthorizationService;
import com.example.valueinsoftbackend.Service.FixAreaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.Positive;
import javax.validation.constraints.PositiveOrZero;
import java.security.Principal;
import java.util.List;

@RestController
@Validated
@RequestMapping("/fixArea")
public class SlotsFixAreaController {

    private final FixAreaService fixAreaService;
    private final AuthorizationService authorizationService;

    @Autowired
    public SlotsFixAreaController(FixAreaService fixAreaService,
                                  AuthorizationService authorizationService) {
        this.fixAreaService = fixAreaService;
        this.authorizationService = authorizationService;
    }

    @RequestMapping(value = "{companyName}/{branchId}/allFixSlots/{month}", method = RequestMethod.GET)
    @ResponseBody
    public ResponseEntity<Object> getBranchFixSlots(
            @PathVariable("companyName") @Positive int companyId,
            @PathVariable @Positive int branchId,
            @PathVariable @PositiveOrZero int month,
            Principal principal
    ) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                branchId,
                "pos.repair.read"
        );
        List<SlotsFixArea> slots = fixAreaService.getFixAreaSlots(companyId, branchId, month);
        return ResponseEntity.accepted().body(slots);
    }

    @PostMapping("{companyName}/{branchId}/addSlot")
    public ResponseEntity<Object> newSlotItem(
            @Valid @RequestBody FixAreaSlotCreateRequest slotsFixArea,
            @PathVariable("companyName") @Positive int companyId,
            @PathVariable @Positive int branchId,
            Principal principal
    ) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                branchId,
                "pos.repair.create"
        );
        return ResponseEntity.status(201).body(fixAreaService.createFixAreaSlot(companyId, branchId, slotsFixArea));
    }

    @PutMapping("{companyName}/update")
    public ResponseEntity<Object> updateSlot(
            @Valid @RequestBody FixAreaSlotUpdateRequest slotsFixArea,
            @PathVariable("companyName") @Positive int companyId,
            Principal principal
    ) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                slotsFixArea.getBranchId(),
                "pos.repair.edit"
        );
        return ResponseEntity.accepted().body(fixAreaService.updateFixAreaSlot(companyId, slotsFixArea));
    }

    @GetMapping("{companyName}/{branchId}/fixSlotById/{faId}")
    public ResponseEntity<SlotsFixArea> getFixSlotById(
            @PathVariable("companyName") @Positive int companyId,
            @PathVariable @Positive int branchId,
            @PathVariable @Positive int faId,
            Principal principal
    ) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                branchId,
                "pos.repair.read"
        );
        return ResponseEntity.ok(fixAreaService.getFixSlotById(companyId, branchId, faId));
    }

    @GetMapping("{companyName}/searchFixArea")
    public ResponseEntity<List<SlotsFixArea>> searchFixSlots(
            @PathVariable("companyName") @Positive int companyId,
            @RequestParam("query") String query,
            Principal principal
    ) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                null,
                "pos.repair.read"
        );
        return ResponseEntity.ok(fixAreaService.searchFixSlots(companyId, query));
    }

    @PutMapping("{companyName}/markPaid")
    public ResponseEntity<Object> markSlotPaid(
            @PathVariable("companyName") @Positive int companyId,
            @RequestParam("slotId") @Positive int slotId,
            @RequestParam("orderId") @Positive int orderId,
            Principal principal
    ) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                null,
                "pos.repair.edit"
        );
        return ResponseEntity.accepted().body(fixAreaService.markSlotPaidAndSave(companyId, slotId, orderId));
    }

    @PutMapping("{companyName}/reverseRepairPayment")
    public ResponseEntity<Object> reverseRepairPayment(
            @PathVariable("companyName") @Positive int companyId,
            @RequestParam("orderId") @Positive int orderId,
            Principal principal
    ) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                null,
                "pos.repair.edit"
        );
        return ResponseEntity.accepted().body(fixAreaService.reverseRepairPayment(companyId, orderId));
    }

    @DeleteMapping("{companyName}/closeSlot")
    public ResponseEntity<Object> closeSlot(
            @PathVariable("companyName") @Positive int companyId,
            @RequestParam("faId") @Positive int faId,
            Principal principal
    ) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                null,
                "pos.repair.edit"
        );
        return ResponseEntity.accepted().body(fixAreaService.closeSlot(companyId, faId));
    }
}
