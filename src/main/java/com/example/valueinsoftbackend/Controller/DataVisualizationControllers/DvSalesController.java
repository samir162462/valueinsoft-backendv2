/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.Controller.DataVisualizationControllers;


import com.example.valueinsoftbackend.Model.DataVisualizationModels.DvSales;
import com.example.valueinsoftbackend.Model.DataVisualizationModels.DVSalesYearly;
import com.example.valueinsoftbackend.Model.Request.SalesOfMonthRequest;
import com.example.valueinsoftbackend.Model.Request.SalesOfYearRequest;
import com.example.valueinsoftbackend.Model.Request.SalesProductsByPeriodRequest;
import com.example.valueinsoftbackend.Service.AuthorizationService;
import com.example.valueinsoftbackend.Service.SalesAnalyticsService;
import com.example.valueinsoftbackend.Model.Sales.SalesProduct;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;

@RestController
@Validated
@RequestMapping("/Dv")
public class DvSalesController {

    private final SalesAnalyticsService salesAnalyticsService;
    private final AuthorizationService authorizationService;

    public DvSalesController(SalesAnalyticsService salesAnalyticsService,
                             AuthorizationService authorizationService) {
        this.salesAnalyticsService = salesAnalyticsService;
        this.authorizationService = authorizationService;
    }

    @RequestMapping(value = "/salesOfMonth", method = RequestMethod.POST)
    public List<DvSales> salesOfMonth(@Valid @RequestBody SalesOfMonthRequest body,
                                      Principal principal) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                body.getCompanyId(),
                body.getBranchId(),
                "pos.sale.read"
        );
        return salesAnalyticsService.getMonthlySales(body);
    }

    @RequestMapping(value = "/salesOfYear", method = RequestMethod.POST)
    public List<DVSalesYearly> salesOfYear(@Valid @RequestBody SalesOfYearRequest body,
                                           Principal principal) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                body.getCompanyId(),
                body.getBranchId(),
                "pos.sale.read"
        );
        return salesAnalyticsService.getYearlySales(body);
    }

    @RequestMapping(value = "/salesProductsByPeriod", method = RequestMethod.POST)
    public List<SalesProduct> salesProductsByPeriod(@Valid @RequestBody SalesProductsByPeriodRequest body,
                                                    Principal principal) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                body.getCompanyId(),
                body.getBranchId(),
                "pos.sale.read"
        );
        return salesAnalyticsService.getSalesProductsByPeriod(body);
    }
}
