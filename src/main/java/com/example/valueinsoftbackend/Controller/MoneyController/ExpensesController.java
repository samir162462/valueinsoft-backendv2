/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.Controller.MoneyController;

import com.example.valueinsoftbackend.Controller.Intefaces.Crud;
import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbExpenses;
import com.example.valueinsoftbackend.Model.Expenses;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/Expenses")
@CrossOrigin("*")
public class ExpensesController implements Crud {


    @Override
    public ResponseEntity<Object> getAll(int companyId, int branchId, String option) {
        return DbExpenses.getPurchasesExpensesByMonth(branchId,companyId,option);
    }

    @Override
    public ResponseEntity<Object> getById(int companyId, int branchId, int oId) {

      //  return DbExpenses.(branchId,companyId,oId);
        return null;
    }

    @Override
    public ResponseEntity<Object> create(Object body, int companyId, int branchId)  {

        try {

            ObjectMapper objectMapper = new ObjectMapper();
            String json = objectMapper.writeValueAsString(body);
            Expenses obj = objectMapper.readValue(json, Expenses.class);
            return DbExpenses.AddExpenses(branchId,companyId,obj,false);

        }catch (Exception e)
        {
            System.out.println(e.getMessage());
        }


        return ResponseEntity.status(HttpStatus.NO_CONTENT).body("error in spring");
    }


    @Override
    public ResponseEntity<Object> updateById(Object body, int companyId, int branchId) {
        try {

            ObjectMapper objectMapper = new ObjectMapper();
            String json = objectMapper.writeValueAsString(body);
            System.out.println(json);
            Expenses obj = objectMapper.readValue(json, Expenses.class);
            return DbExpenses.updateExpenses(branchId,companyId,obj);

        }catch (Exception e)
        {
            System.out.println(e.getMessage());
        }


        return ResponseEntity.status(HttpStatus.NO_CONTENT).body("error in spring");

    }

    @Override
    public ResponseEntity<Object> DeleteById(int companyId, int branchId) {
        return null;
    }


}
