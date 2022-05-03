/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.Controller.PosController;

import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbSlotsFixArea;
import com.example.valueinsoftbackend.Model.Slots.SlotsFixArea;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/fixArea")
@CrossOrigin("*")
public class SlotsFixAreaController {
    @RequestMapping(value = "{companyName}/{branchId}/allFixSlots", method = RequestMethod.GET)
    @ResponseBody
    public ResponseEntity<Object> getBranchFixSlots(

            @PathVariable int branchId,
            @PathVariable String companyName
    ) {
        return DbSlotsFixArea.getFixAreaSlot(branchId, companyName);
    }

    @PostMapping("{companyName}/{branchId}/addSlot")
    public ResponseEntity<Object> newSlotItem(
            @RequestBody SlotsFixArea slotsFixArea,
            @PathVariable int branchId,
            @PathVariable String companyName
    ) {
        return DbSlotsFixArea.AddFixAreaSlot(branchId, companyName, slotsFixArea);

    }

    @PutMapping("{companyName}/update")
    public ResponseEntity<Object> updateSlot(
            @RequestBody SlotsFixArea slotsFixArea,
            @PathVariable String companyName
    ) {
        return DbSlotsFixArea.updateFixAreaSlot( companyName, slotsFixArea);

    }
}
