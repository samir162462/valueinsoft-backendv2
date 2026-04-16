/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.Controller.DataVisualizationControllers;

import com.example.valueinsoftbackend.Model.Request.CompanySalesWindowRequest;
import com.example.valueinsoftbackend.Model.DataVisualizationModels.DvCompanyChartSalesIncome;
import com.example.valueinsoftbackend.Service.AuthorizationService;
import com.example.valueinsoftbackend.Service.SalesAnalyticsService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.Map;



@RestController
@Validated
@RequestMapping("/DvCompany")
public class DVSalesCompanyController {

    private final SalesAnalyticsService salesAnalyticsService;
    private final AuthorizationService authorizationService;

    public DVSalesCompanyController(SalesAnalyticsService salesAnalyticsService,
                                    AuthorizationService authorizationService) {
        this.salesAnalyticsService = salesAnalyticsService;
        this.authorizationService = authorizationService;
    }



    @RequestMapping(value = "/salesOfCompany",method = RequestMethod.POST)
    public List<Map<String, Object>> currentShiftBranchesTotalAndIncome(@Valid @RequestBody CompanySalesWindowRequest body,
                                                                       Principal principal)
    {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                body.getCompanyId(),
                null,
                "finance.report.read"
        );
        return salesAnalyticsService.getCompanySalesWindow(body);
    }
//getShiftTotalAndIncomeOfAllBranchesPerDay

    @RequestMapping(value = "/salesOfCompanyPerDay",method = RequestMethod.POST)
    public List<DvCompanyChartSalesIncome> currentShiftBranchesTotalAndIncomePerDay(@Valid @RequestBody CompanySalesWindowRequest body,
                                                                                    Principal principal)
    {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                body.getCompanyId(),
                null,
                "finance.report.read"
        );
        return salesAnalyticsService.getCompanySalesWindowPerDay(body);
    }


}
