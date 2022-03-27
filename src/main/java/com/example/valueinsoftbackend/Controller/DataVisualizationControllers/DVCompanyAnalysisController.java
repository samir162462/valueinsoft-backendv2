/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.Controller.DataVisualizationControllers;


import com.example.valueinsoftbackend.DatabaseRequests.DbDVCompanyAnalysis;
import com.example.valueinsoftbackend.Model.DataVisualizationModels.CompanyAnalysis;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Map;

@RestController
@RequestMapping("/DvCa")
@CrossOrigin("*")
public class DVCompanyAnalysisController {

    @RequestMapping(value = "/CompanyAnalysis",method = RequestMethod.POST)
    public ArrayList<CompanyAnalysis> analysisOfMonth(@RequestBody Map<String,Object> body  ) throws Exception
    {
        int branchId = (int) body.get("branchId");
        int companyId = (int) body.get("companyId");
        //String currentMonth = body.get("currentMonth").toString();
        LocalDate todaydate = LocalDate.now();
        System.out.println("Months first date in yyyy-mm-dd: " +todaydate.withDayOfMonth(1));
        System.out.println("in");

        return DbDVCompanyAnalysis.getCompanyAnalysis(branchId,companyId,null,todaydate.withDayOfMonth(1).toString()); // byWhat is byDefault (month).

    }
}
