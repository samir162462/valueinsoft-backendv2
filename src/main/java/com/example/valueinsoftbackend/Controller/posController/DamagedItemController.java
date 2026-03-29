/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.Controller.posController;

import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosDamagedList;
import com.example.valueinsoftbackend.Model.DamagedItem;
import com.example.valueinsoftbackend.Model.Request.SupplierUpdateRequest;
import com.example.valueinsoftbackend.Model.Supplier;
import com.example.valueinsoftbackend.Service.SupplierService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/DamagedItem")
public class DamagedItemController {

    DbPosDamagedList dbPosDamagedList;
    private final SupplierService supplierService;

    @Autowired
    public DamagedItemController(DbPosDamagedList dbPosDamagedList, SupplierService supplierService) {
        this.dbPosDamagedList = dbPosDamagedList;
        this.supplierService = supplierService;
    }

    @RequestMapping(value = "{companyName}/{branchId}/all", method = RequestMethod.GET)
    @ResponseBody
    public ArrayList<DamagedItem> getDamagesItem(
            @PathVariable int branchId,
            @PathVariable String companyName
    ) {
        return dbPosDamagedList.getDamagedList(branchId, companyName);
    }

    @PostMapping("{companyName}/{branchId}/add")
    public ResponseEntity<Object> newDamagedItem(
            @RequestBody DamagedItem damagedItem,
            @PathVariable int branchId,
            @PathVariable String companyName
    ) {
        String answer = dbPosDamagedList.AddDamagedItem(branchId, companyName, damagedItem);

        HashMap<String, String> res = new HashMap<>();
        res.put("Message", answer);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(res);
    }

    @RequestMapping(value = "{companyId}/{branchId}/update/{id}", method = RequestMethod.PUT)
    public Map<String, String> updateSupplier(
            @RequestBody Supplier supplier,
            @PathVariable int branchId,
            @PathVariable int companyId

    ) {
        SupplierUpdateRequest request = new SupplierUpdateRequest();
        request.setSupplierId(supplier.getSupplierId());
        request.setSupplierName(supplier.getSupplierName());
        request.setSupplierPhone1(supplier.getSupplierPhone1());
        request.setSupplierPhone2(supplier.getSupplierPhone2());
        request.setSuplierLocation(supplier.getSuplierLocation());
        request.setSuplierMajor(supplier.getSuplierMajor());

        Map<String, String> response = new HashMap<>();
        response.put("Response", supplierService.updateSupplier(companyId, branchId, supplier.getSupplierId(), request));
        return response;
    }

    @DeleteMapping("{companyName}/{branchId}/delete/{DId}")
    public Map<String, Boolean> deleteDamagedItem(
            @PathVariable(value = "DId") int DId,
            @PathVariable int branchId,
            @PathVariable String companyName
    ) throws Exception {
        Map<String, Boolean> response = new HashMap<>();
        boolean bol = DbPosDamagedList.deleteDamagedItem(branchId, companyName, DId);
        response.put("deleted", bol);
        return response;
    }
}
