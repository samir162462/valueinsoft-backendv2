/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.Controller;


import com.example.valueinsoftbackend.DatabaseRequests.DbSupplier;
import com.example.valueinsoftbackend.Model.InventoryTransaction;
import com.example.valueinsoftbackend.Model.Supplier;
import com.google.gson.JsonObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/suppliers")
@CrossOrigin("*")
public class SupplierController {

    @RequestMapping(value = "/all/{id}", method = RequestMethod.GET)
    @ResponseBody
    public ArrayList<Supplier> getsuppliers(

            @PathVariable int id
    ) {


        return DbSupplier.getSuppliers(id);
    }


    @RequestMapping(value = "{branchId}/remain/{productId}", method = RequestMethod.GET)
    @ResponseBody
    public String getRemaining(

            @PathVariable int productId,
            @PathVariable int branchId
    ) {

        return DbSupplier.getRemainingSupplierAmountByProductId(productId, branchId).toString();
    }

    @PostMapping("/saveSupplier")

    public ResponseEntity<Object> newSupplier(@RequestBody Map<String, String> requestBody) {


        String answer = DbSupplier.AddSupplier(requestBody.get("supplierName"), requestBody.get("supplierPhone1"), requestBody.get("supplierPhone2"), requestBody.get("suplierLocation"), requestBody.get("suplierMajor"), Integer.valueOf(requestBody.get("branchId")));


        return ResponseEntity.status(HttpStatus.ACCEPTED).body(answer);

    }


    @RequestMapping(value = "{branchId}/update/{id}", method = RequestMethod.PUT)
    public Map<String, String> updateSupplier(
            @RequestBody Supplier supplier,
            @PathVariable int branchId

    ) {

        //todo --update method
        Map<String, String> response = new HashMap<>();
        response.put("Response", DbSupplier.updateSupplier(supplier, branchId));
        return response;
    }

    //todo --Delete
    @DeleteMapping("{branchId}/delete/{id}")
    public Map<String, Boolean> deleteUser(
            @PathVariable(value = "id") int supplierId,
            @PathVariable int branchId
    ) throws Exception {
        Map<String, Boolean> response = new HashMap<>();
        boolean bol = DbSupplier.deleteSupp(supplierId, branchId);
        response.put("deleted", bol);
        return response;
    }

    @RequestMapping(value = "{branchId}/SupplierSales/{supplierId}", method = RequestMethod.GET)
    @ResponseBody
    public ArrayList<InventoryTransaction> getSupplierSales(

            @PathVariable int supplierId,
            @PathVariable int branchId
    ) {

        return DbSupplier.getSupplierSales(branchId, supplierId);
    }

}
