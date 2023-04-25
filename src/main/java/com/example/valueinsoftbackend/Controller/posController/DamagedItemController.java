/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.Controller.posController;

import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosDamagedList;
import com.example.valueinsoftbackend.DatabaseRequests.DbSupplier;
import com.example.valueinsoftbackend.Model.DamagedItem;
import com.example.valueinsoftbackend.Model.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


@RestController
@RequestMapping("/DamagedItem")
@CrossOrigin("*")
public class DamagedItemController {

    DbPosDamagedList dbPosDamagedList;
@Autowired
    public DamagedItemController(DbPosDamagedList dbPosDamagedList) {
        this.dbPosDamagedList = dbPosDamagedList;
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

    //TODO UPDATE
    @RequestMapping(value = "{companyId}/{branchId}/update/{id}", method = RequestMethod.PUT)
    public Map<String, String> updateSupplier(
            @RequestBody Supplier supplier,
            @PathVariable int branchId,
            @PathVariable int companyId

    ) {

        Map<String, String> response = new HashMap<>();
        response.put("Response", DbSupplier.updateSupplier(supplier, branchId,companyId));
        return response;
    }

    //todo --Delete
    @DeleteMapping("{companyName}/{branchId}/delete/{DId}")
    public Map<String, Boolean> deleteDamagedItem(
            @PathVariable(value = "DId") int DId,
            @PathVariable int branchId,
            @PathVariable String companyName
    ) throws Exception {
        Map<String, Boolean> response = new HashMap<>();
        boolean bol = DbPosDamagedList.deleteDamagedItem( branchId , companyName,DId);
        response.put("deleted", bol);
        return response;
    }



}
