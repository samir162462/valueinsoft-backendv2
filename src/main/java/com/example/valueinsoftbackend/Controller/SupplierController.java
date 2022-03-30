/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.Controller;


import com.example.valueinsoftbackend.DatabaseRequests.DbSupplier;
import com.example.valueinsoftbackend.Model.InventoryTransaction;
import com.example.valueinsoftbackend.Model.Supplier;
import com.example.valueinsoftbackend.Model.SupplierBProduct;
import com.google.gson.JsonObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/suppliers")
@CrossOrigin("*")
public class SupplierController {

    @RequestMapping(value = "/all/{companyId}/{branchId}", method = RequestMethod.GET)
    @ResponseBody
    public ArrayList<Supplier> getsuppliers(

            @PathVariable int companyId,
            @PathVariable int branchId
    ) {


        return DbSupplier.getSuppliers(branchId, companyId);
    }


    @RequestMapping(value = "{companyId}{branchId}/remain/{productId}", method = RequestMethod.GET)
    @ResponseBody
    public String getRemaining(

            @PathVariable int productId,
            @PathVariable int companyId,
            @PathVariable int branchId
    ) {

        return DbSupplier.getRemainingSupplierAmountByProductId(productId, branchId, companyId).toString();
    }

    @PostMapping("/saveSupplier")

    public ResponseEntity<Object> newSupplier(@RequestBody Map<String, String> requestBody) {


        String answer = DbSupplier.AddSupplier(requestBody.get("supplierName"), requestBody.get("supplierPhone1"), requestBody.get("supplierPhone2"), requestBody.get("suplierLocation"), requestBody.get("suplierMajor"), Integer.valueOf(requestBody.get("branchId")), Integer.valueOf(requestBody.get("companyId")));


        return ResponseEntity.status(HttpStatus.ACCEPTED).body(answer);

    }


    @RequestMapping(value = "{companyId}/{branchId}/update/{id}", method = RequestMethod.PUT)
    public Map<String, String> updateSupplier(
            @RequestBody Supplier supplier,
            @PathVariable int branchId,
            @PathVariable int companyId

    ) {

        //todo --update method
        Map<String, String> response = new HashMap<>();
        response.put("Response", DbSupplier.updateSupplier(supplier, branchId, companyId));
        return response;
    }

    //todo --Delete
    @DeleteMapping("{companyId}/{branchId}/delete/{id}")
    public Map<String, Boolean> deleteUser(
            @PathVariable(value = "id") int supplierId,
            @PathVariable int branchId,
            @PathVariable int companyId

    ) throws Exception {
        Map<String, Boolean> response = new HashMap<>();
        boolean bol = DbSupplier.deleteSupp(supplierId, branchId, companyId);
        response.put("deleted", bol);
        return response;
    }

    @RequestMapping(value = "{companyId}/{branchId}/SupplierSales/{supplierId}", method = RequestMethod.GET)
    @ResponseBody
    public ArrayList<InventoryTransaction> getSupplierSales(

            @PathVariable int supplierId,
            @PathVariable int companyId,
            @PathVariable int branchId
    ) {

        return DbSupplier.getSupplierSales(branchId, supplierId, companyId);
    }

    @RequestMapping(value = "{companyId}/{branchId}/SupplierBProduct/{supplierId}", method = RequestMethod.GET)
    @ResponseBody
    public ArrayList<SupplierBProduct> getSupplierBProduct(

            @PathVariable int supplierId,
            @PathVariable int companyId,
            @PathVariable int branchId
    ) {

        return DbSupplier.getSupplierBProduct(branchId, supplierId, companyId);
    }


    @PostMapping("{companyId}/{branchId}/saveSupplierBProduct/{productId}")
    public ResponseEntity<Object> newSupplierBProduct(
            @PathVariable int productId,
            @PathVariable int companyId,
            @PathVariable int branchId,
            @RequestBody SupplierBProduct requestBody
    ) {
        try {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(DbSupplier.AddSupplierBProduct(requestBody,productId,branchId,companyId ));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("error");

        }

    }
}
