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
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ExpensesStatic")
@CrossOrigin("*")
public class ExpensesStaticController implements Crud {

    @Override
    public ResponseEntity<Object> getAll(int companyId, int branchId, String option) {
        return DbExpenses.getAllExpensesItems(branchId,companyId,true);
    }

    @Override
    public ResponseEntity<Object> getById(int companyId, int branchId, int oId) {
        return null;
    }

    @Override
    public ResponseEntity<Object> create(Object body, int companyId, int branchId) {
        try {

            ObjectMapper objectMapper = new ObjectMapper();
            String json = objectMapper.writeValueAsString(body);
            Expenses obj = objectMapper.readValue(json, Expenses.class);
            return DbExpenses.AddExpenses(branchId,companyId,obj,true);

        }catch (Exception e)
        {
            System.out.println(e.getMessage());
        }


        return ResponseEntity.status(HttpStatus.NO_CONTENT).body("error in spring");
    }

    @Override
    public ResponseEntity<Object> updateById(Object body, int companyId, int branchId) {
        return null;
    }

    @Override
    public ResponseEntity<Object> DeleteById(int companyId, int branchId) {
        return null;
    }
}
