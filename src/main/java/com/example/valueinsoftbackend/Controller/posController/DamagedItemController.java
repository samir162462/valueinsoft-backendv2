/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.Controller.posController;

import com.example.valueinsoftbackend.Model.DamagedItem;
import com.example.valueinsoftbackend.Model.Request.CreateDamagedItemRequest;
import com.example.valueinsoftbackend.Model.Request.SupplierUpdateRequest;
import com.example.valueinsoftbackend.Service.DamagedItemService;
import com.example.valueinsoftbackend.Service.SupplierService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.validation.Valid;
import javax.validation.constraints.Positive;

@RestController
@Validated
@RequestMapping("/DamagedItem")
public class DamagedItemController {

    private final DamagedItemService damagedItemService;
    private final SupplierService supplierService;

    @Autowired
    public DamagedItemController(DamagedItemService damagedItemService, SupplierService supplierService) {
        this.damagedItemService = damagedItemService;
        this.supplierService = supplierService;
    }

    @RequestMapping(value = "{companyName}/{branchId}/all", method = RequestMethod.GET)
    @ResponseBody
    public List<DamagedItem> getDamagesItem(
            @PathVariable("companyName") @Positive int companyId,
            @PathVariable @Positive int branchId
    ) {
        return damagedItemService.getDamagedItems(companyId, branchId);
    }

    @PostMapping("{companyName}/{branchId}/add")
    public ResponseEntity<Object> newDamagedItem(
            @Valid @RequestBody CreateDamagedItemRequest damagedItem,
            @PathVariable("companyName") @Positive int companyId,
            @PathVariable @Positive int branchId
    ) {
        String answer = damagedItemService.addDamagedItem(companyId, branchId, damagedItem);

        HashMap<String, String> res = new HashMap<>();
        res.put("Message", answer);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(res);
    }

    @RequestMapping(value = "{companyId}/{branchId}/update/{id}", method = RequestMethod.PUT)
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

    @DeleteMapping("{companyName}/{branchId}/delete/{DId}")
    public Map<String, Boolean> deleteDamagedItem(
            @PathVariable(value = "DId") @Positive int DId,
            @PathVariable("companyName") @Positive int companyId,
            @PathVariable @Positive int branchId
    ) throws Exception {
        Map<String, Boolean> response = new HashMap<>();
        boolean bol = damagedItemService.deleteDamagedItem(companyId, branchId, DId);
        response.put("deleted", bol);
        return response;
    }
}
