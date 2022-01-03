/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.Controller;


import com.example.valueinsoftbackend.DatabaseRequests.DbSupplier;
import com.example.valueinsoftbackend.Model.Supplier;
import com.google.gson.JsonObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
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


        return  DbSupplier.getSuppliers(id);
    }



    @RequestMapping(value = "{branchId}/remain/{productId}", method = RequestMethod.GET)
    @ResponseBody
    public String getRemaining(

            @PathVariable int productId,
            @PathVariable int branchId
    ) {


        return  DbSupplier.getRemainingSupplierAmountByProductId(productId,branchId).toString();
    }

    @PostMapping("/saveSupplier")

    public ResponseEntity<Object> newUser(@RequestBody Map<String, String> requestBody) {


        String answer = DbSupplier.AddSupplier(requestBody.get("supplierName"),requestBody.get("supplierPhone1"),requestBody.get("supplierPhone2"),requestBody.get("location"),Integer.valueOf(requestBody.get("branchId"))   );



        return ResponseEntity.status(HttpStatus.ACCEPTED).body(answer);

    }



}
