/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.Controller.posController;

import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosInventoryTransaction;
import com.example.valueinsoftbackend.Model.InventoryTransaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
@RestController
@RequestMapping("/invTrans")
@CrossOrigin("*")
public class InventoryTransactionController {


    DbPosInventoryTransaction dbPosInventoryTransaction;

    @Autowired
    public InventoryTransactionController(DbPosInventoryTransaction dbPosInventoryTransaction) {
        this.dbPosInventoryTransaction = dbPosInventoryTransaction;
    }

    @PostMapping("/AddTransaction")

    public ResponseEntity<Object> newTransaction(@RequestBody Map<String,Object> body) {
        int productId = (int) body.get("productId");
        String userName = body.get("userName").toString();
        int supplierId = (int) body.get("supplierId");
        String transactionType = body.get("transactionType").toString();
        int numItems = (int) body.get("numItems");
        int transTotal = (int) body.get("transTotal");
        String payType = body.get("payType").toString();
        String time = body.get("time").toString();
        int remainingAmount = (int) body.get("remainingAmount");
        int branchId = (int) body.get("branchId");
        int companyId = (int) body.get("companyId");
        Timestamp timestamp = null;
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss.SSS"); //"2021-10-02 18:48:05.123"
            Date parsedDate = dateFormat.parse(time);
             timestamp = new java.sql.Timestamp(parsedDate.getTime());
        }catch (Exception e)
        {

            return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).body("{\"title\" : \"the timestamp error\", \"branchId\" : "+branchId+"}" );

        }

        try {

            dbPosInventoryTransaction.AddTransactionToInv(productId,userName,supplierId,transactionType,numItems,transTotal,payType,timestamp,remainingAmount,branchId,companyId);
        }catch (Exception e )
        {
            System.out.println(e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).body("{\"title\" : \"the timestamp error\", \"branchId\" : "+branchId+"}" );

        }
        return ResponseEntity.status(HttpStatus.CREATED).body("{\"title\" : \"the newTransaction added \", \"branchId\" : "+branchId+"}" );

    }


    @PostMapping("/transactions")

    public ResponseEntity<Object> newUser(@RequestBody Map<String,Object> body) {
        int branchId = (int) body.get("branchId");
        String startTime = body.get("startTime").toString();
        String endTime = body.get("endTime").toString();
        int companyId = (int) body.get("companyId");

        System.out.println(startTime);
        System.out.println(endTime);
        ArrayList<InventoryTransaction> inventoryTransactions = null;
        try {

            inventoryTransactions = dbPosInventoryTransaction.getInventoryTrans(companyId,branchId,startTime,endTime);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(inventoryTransactions);

        }catch (Exception e )
        {
            return ResponseEntity.status(HttpStatus.NO_CONTENT).body(null);
        }

    }

}
