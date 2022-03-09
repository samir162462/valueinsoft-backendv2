/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.Controller.DataVisualizationControllers;

import com.example.valueinsoftbackend.DatabaseRequests.DbBranch;
import com.example.valueinsoftbackend.DatabaseRequests.DbDataVisualization.Sales.DbDvCompany;
import com.example.valueinsoftbackend.DatabaseRequests.DbDataVisualization.Sales.DbDvSales;
import com.example.valueinsoftbackend.Model.Branch;
import com.example.valueinsoftbackend.Model.DataVisualizationModels.DvCompanyChartSalesIncome;
import com.example.valueinsoftbackend.Model.DataVisualizationModels.DvSales;
import com.google.gson.JsonArray;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Map;



@RestController
@RequestMapping("/DvCompany")
@CrossOrigin("*")
public class DVSalesCompanyController {

    @RequestMapping(value = "/salesOfCompany",method = RequestMethod.POST)
    public String currentShiftBranchesTotalAndIncome(@RequestBody Map<String,Object> body  ) throws Exception
    {

        ArrayList<Branch> branchArrayList = DbBranch.getBranchByCompanyId(Integer.valueOf(body.get("companyId").toString()));

        return DbDvCompany.getShiftTotalAndIncomeOfAllBranches(Integer.valueOf(body.get("companyId").toString()),branchArrayList,body.get("hours").toString()).toString();

    }
//getShiftTotalAndIncomeOfAllBranchesPerDay

    @RequestMapping(value = "/salesOfCompanyPerDay",method = RequestMethod.POST)
    public ArrayList<DvCompanyChartSalesIncome> currentShiftBranchesTotalAndIncomePerDay(@RequestBody Map<String,Object> body  ) throws Exception
    {

        ArrayList<Branch> branchArrayList = DbBranch.getBranchByCompanyId(Integer.valueOf(body.get("companyId").toString()));

        return DbDvCompany.getShiftTotalAndIncomeOfAllBranchesPerDay(Integer.valueOf(body.get("companyId").toString()),branchArrayList,body.get("hours").toString());

    }


}
