/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.Controller.DataVisualizationControllers;



import com.example.valueinsoftbackend.DatabaseRequests.DbDataVisualization.Sales.DbDvSales;
import com.example.valueinsoftbackend.Model.DataVisualizationModels.DvSales;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.*;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;

@RestController
@RequestMapping("/Dv")
@CrossOrigin("*")
public class DvSalesController {

    @RequestMapping(value = "/salesOfMonth",method = RequestMethod.POST)
    public ArrayList<DvSales> salesOfMonth(@RequestBody Map<String,Object> body  ) throws Exception
    {
        int branchId = (int) body.get("branchId");
        String currentMonth = body.get("currentMonth").toString();

        System.out.println("in");

        return DbDvSales.getMonthlySales(currentMonth,branchId);

    }
}
