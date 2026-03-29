/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.Controller.DataVisualizationControllers;


import com.example.valueinsoftbackend.Model.DataVisualizationModels.DvSales;
import com.example.valueinsoftbackend.Model.DataVisualizationModels.DVSalesYearly;
import com.example.valueinsoftbackend.Model.Request.SalesOfMonthRequest;
import com.example.valueinsoftbackend.Model.Request.SalesOfYearRequest;
import com.example.valueinsoftbackend.Model.Request.SalesProductsByPeriodRequest;
import com.example.valueinsoftbackend.Service.SalesAnalyticsService;
import com.example.valueinsoftbackend.Model.Sales.SalesProduct;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@Validated
@RequestMapping("/Dv")
public class DvSalesController {

    private final SalesAnalyticsService salesAnalyticsService;

    public DvSalesController(SalesAnalyticsService salesAnalyticsService) {
        this.salesAnalyticsService = salesAnalyticsService;
    }

    @RequestMapping(value = "/salesOfMonth", method = RequestMethod.POST)
    public List<DvSales> salesOfMonth(@Valid @RequestBody SalesOfMonthRequest body) {
        return salesAnalyticsService.getMonthlySales(body);
    }

    @RequestMapping(value = "/salesOfYear", method = RequestMethod.POST)
    public List<DVSalesYearly> salesOfYear(@Valid @RequestBody SalesOfYearRequest body) {
        return salesAnalyticsService.getYearlySales(body);
    }

    @RequestMapping(value = "/salesProductsByPeriod", method = RequestMethod.POST)
    public List<SalesProduct> salesProductsByPeriod(@Valid @RequestBody SalesProductsByPeriodRequest body) {
        return salesAnalyticsService.getSalesProductsByPeriod(body);
    }
}
