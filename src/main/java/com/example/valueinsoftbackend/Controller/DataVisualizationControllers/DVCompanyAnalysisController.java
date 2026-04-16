/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.Controller.DataVisualizationControllers;


import com.example.valueinsoftbackend.Model.DataVisualizationModels.CompanyAnalysis;
import com.example.valueinsoftbackend.Model.Request.CompanyAnalysisRequest;
import com.example.valueinsoftbackend.Model.Request.CompanyAnalysisUpdateRequest;
import com.example.valueinsoftbackend.Service.AuthorizationService;
import com.example.valueinsoftbackend.Service.CompanyAnalysisService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;

@RestController
@Validated
@RequestMapping("/DvCa")
public class DVCompanyAnalysisController {

    private final CompanyAnalysisService companyAnalysisService;
    private final AuthorizationService authorizationService;

    public DVCompanyAnalysisController(CompanyAnalysisService companyAnalysisService,
                                       AuthorizationService authorizationService) {
        this.companyAnalysisService = companyAnalysisService;
        this.authorizationService = authorizationService;
    }

    @RequestMapping(value = "/CompanyAnalysis", method = RequestMethod.POST)
    public List<CompanyAnalysis> analysisOfMonth(@Valid @RequestBody CompanyAnalysisRequest body,
                                                 Principal principal) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                body.getCompanyId(),
                body.getBranchId(),
                "finance.report.read"
        );
        return companyAnalysisService.getCurrentMonthAnalysis(body);
    }

    @RequestMapping(value = "/CompanyAnalysisUpdate", method = RequestMethod.PUT)
    public String CompanyAnalysisUpdate(@Valid @RequestBody CompanyAnalysisUpdateRequest body,
                                        Principal principal) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                body.getCompanyId(),
                body.getBranchId(),
                "finance.entry.edit"
        );
        return companyAnalysisService.incrementCurrentDay(body);
    }
}
