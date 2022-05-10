/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.Controller.DataVisualizationControllers;


import com.example.valueinsoftbackend.DatabaseRequests.DbDataVisualization.Sales.DbDvSales;
import com.example.valueinsoftbackend.Model.DataVisualizationModels.DvSales;
import com.example.valueinsoftbackend.Model.Sales.SalesProduct;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Map;

@RestController
@RequestMapping("/Dv")
@CrossOrigin("*")
public class DvSalesController {

    @RequestMapping(value = "/salesOfMonth", method = RequestMethod.POST)
    public ArrayList<DvSales> salesOfMonth(@RequestBody Map<String, Object> body) throws Exception {
        int branchId = (int) body.get("branchId");
        int companyId = (int) body.get("companyId");
        String currentMonth = body.get("currentMonth").toString();

        System.out.println("in");

        return DbDvSales.getMonthlySales(companyId, currentMonth, branchId);

    }

    @RequestMapping(value = "/salesOfYear", method = RequestMethod.POST)
    public ResponseEntity<Object> salesOfYear(@RequestBody Map<String, Object> body) throws Exception {
        int branchId = (int) body.get("branchId");
        int companyId = (int) body.get("companyId");
        String currentMonth = body.get("year").toString();

        System.out.println("in");

        return DbDvSales.getYearlySales(companyId, currentMonth, branchId);

    }

    @RequestMapping(value = "/salesProductsByPeriod", method = RequestMethod.POST)
    public ArrayList<SalesProduct> salesProductsByPeriod(@RequestBody Map<String, Object> body) throws Exception {

        int branchId = (int) body.get("branchId");
        System.out.println("CompanyId: ");

        int companyId = (int) body.get("companyId");

        String startTime = body.get("startTime").toString();
        String endTime = body.get("endTime").toString();

        System.out.println("CompanyId: " + companyId);

        return DbDvSales.getSalesProductsByPeriod(companyId, branchId, startTime, endTime);

    }
}
