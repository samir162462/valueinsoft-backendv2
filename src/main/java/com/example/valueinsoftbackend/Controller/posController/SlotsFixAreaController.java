/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.Controller.posController;

import com.example.valueinsoftbackend.Model.Request.FixAreaSlotCreateRequest;
import com.example.valueinsoftbackend.Model.Request.FixAreaSlotUpdateRequest;
import com.example.valueinsoftbackend.Model.Slots.SlotsFixArea;
import com.example.valueinsoftbackend.Service.FixAreaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.Positive;
import javax.validation.constraints.PositiveOrZero;
import java.util.List;

@RestController
@Validated
@RequestMapping("/fixArea")
public class SlotsFixAreaController {

    private final FixAreaService fixAreaService;

    @Autowired
    public SlotsFixAreaController(FixAreaService fixAreaService) {
        this.fixAreaService = fixAreaService;
    }

    @RequestMapping(value = "{companyName}/{branchId}/allFixSlots/{month}", method = RequestMethod.GET)
    @ResponseBody
    public ResponseEntity<Object> getBranchFixSlots(
            @PathVariable("companyName") @Positive int companyId,
            @PathVariable @Positive int branchId,
            @PathVariable @PositiveOrZero int month
    ) {
        List<SlotsFixArea> slots = fixAreaService.getFixAreaSlots(companyId, branchId, month);
        return ResponseEntity.accepted().body(slots);
    }

    @PostMapping("{companyName}/{branchId}/addSlot")
    public ResponseEntity<Object> newSlotItem(
            @Valid @RequestBody FixAreaSlotCreateRequest slotsFixArea,
            @PathVariable("companyName") @Positive int companyId,
            @PathVariable @Positive int branchId
    ) {
        return ResponseEntity.status(201).body(fixAreaService.createFixAreaSlot(companyId, branchId, slotsFixArea));
    }

    @PutMapping("{companyName}/update")
    public ResponseEntity<Object> updateSlot(
            @Valid @RequestBody FixAreaSlotUpdateRequest slotsFixArea,
            @PathVariable("companyName") @Positive int companyId
    ) {
        return ResponseEntity.accepted().body(fixAreaService.updateFixAreaSlot(companyId, slotsFixArea));
    }
}
