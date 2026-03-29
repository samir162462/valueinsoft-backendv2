/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.Controller.DataVisualizationControllers;

import com.example.valueinsoftbackend.Model.Request.CompanySalesWindowRequest;
import com.example.valueinsoftbackend.Model.DataVisualizationModels.DvCompanyChartSalesIncome;
import com.example.valueinsoftbackend.Service.SalesAnalyticsService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;



@RestController
@Validated
@RequestMapping("/DvCompany")
public class DVSalesCompanyController {

    private final SalesAnalyticsService salesAnalyticsService;

    public DVSalesCompanyController(SalesAnalyticsService salesAnalyticsService) {
        this.salesAnalyticsService = salesAnalyticsService;
    }



    @RequestMapping(value = "/salesOfCompany",method = RequestMethod.POST)
    public List<Map<String, Object>> currentShiftBranchesTotalAndIncome(@Valid @RequestBody CompanySalesWindowRequest body)
    {
        return salesAnalyticsService.getCompanySalesWindow(body);
    }
//getShiftTotalAndIncomeOfAllBranchesPerDay

    @RequestMapping(value = "/salesOfCompanyPerDay",method = RequestMethod.POST)
    public List<DvCompanyChartSalesIncome> currentShiftBranchesTotalAndIncomePerDay(@Valid @RequestBody CompanySalesWindowRequest body)
    {
        return salesAnalyticsService.getCompanySalesWindowPerDay(body);
    }


}
