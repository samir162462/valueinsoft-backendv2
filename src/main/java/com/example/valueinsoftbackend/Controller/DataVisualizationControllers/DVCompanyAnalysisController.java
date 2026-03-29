/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.Controller.DataVisualizationControllers;


import com.example.valueinsoftbackend.Model.DataVisualizationModels.CompanyAnalysis;
import com.example.valueinsoftbackend.Model.Request.CompanyAnalysisRequest;
import com.example.valueinsoftbackend.Model.Request.CompanyAnalysisUpdateRequest;
import com.example.valueinsoftbackend.Service.CompanyAnalysisService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@Validated
@RequestMapping("/DvCa")
public class DVCompanyAnalysisController {

    private final CompanyAnalysisService companyAnalysisService;

    public DVCompanyAnalysisController(CompanyAnalysisService companyAnalysisService) {
        this.companyAnalysisService = companyAnalysisService;
    }

    @RequestMapping(value = "/CompanyAnalysis", method = RequestMethod.POST)
    public List<CompanyAnalysis> analysisOfMonth(@Valid @RequestBody CompanyAnalysisRequest body) {
        return companyAnalysisService.getCurrentMonthAnalysis(body);
    }

    @RequestMapping(value = "/CompanyAnalysisUpdate", method = RequestMethod.PUT)
    public String CompanyAnalysisUpdate(@Valid @RequestBody CompanyAnalysisUpdateRequest body) {
        return companyAnalysisService.incrementCurrentDay(body);
    }
}
