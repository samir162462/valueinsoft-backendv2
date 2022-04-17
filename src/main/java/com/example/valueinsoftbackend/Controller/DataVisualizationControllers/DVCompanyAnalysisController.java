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

    @RequestMapping(value = "/CompanyAnalysis", method = RequestMethod.POST)
    public ArrayList<CompanyAnalysis> analysisOfMonth(@RequestBody Map<String, Object> body) throws Exception {
        int branchId = (int) body.get("branchId");
        int companyId = (int) body.get("companyId");
        //String currentMonth = body.get("currentMonth").toString();
        LocalDate todaydate = LocalDate.now();
        System.out.println("Months first date in yyyy-mm-dd: " + todaydate.withDayOfMonth(1));
        System.out.println("in");

        if (branchId == 0) {

        } else {
            if (DbDVCompanyAnalysis.isTableTimeRecordBranchDVCA(branchId, companyId) == true) {
                System.out.println("DbDVCompanyAnalysis.isTableTimeRecordBranchDVCA(branchId,companyId) == true");
                return DbDVCompanyAnalysis.getCompanyAnalysis(branchId, companyId, null, todaydate.withDayOfMonth(1).toString()); // byWhat is byDefault (month).
            } else {
                System.out.println("DbDVCompanyAnalysis.isTableTimeRecordBranchDVCA(branchId,companyId) == false");

                DbDVCompanyAnalysis.AddRecordBranchDVCA(branchId, companyId); // byWhat is byDefault (month).
            }
        }


        return DbDVCompanyAnalysis.getCompanyAnalysis(branchId, companyId, null, todaydate.withDayOfMonth(1).toString()); // byWhat is byDefault (month).

    }

    @RequestMapping(value = "/CompanyAnalysisUpdate", method = RequestMethod.PUT)
    public String CompanyAnalysisUpdate(@RequestBody Map<String, Object> body) throws Exception {
        int branchId = (int) body.get("branchId");
        int companyId = (int) body.get("companyId");
        int sales = (int) body.get("sales");
        int income = (int) body.get("income");
        int clientIn = (int) body.get("clientIn");
        int invShortage = (int) body.get("invShortage");
        int discountByUser = (int) body.get("discountByUser");
        int damagedProducts = (int) body.get("damagedProducts");
        int returnPurchases = (int) body.get("returnPurchases");
        int shiftEndsEarly = (int) body.get("shiftEndsEarly");

        System.out.println(branchId);
        System.out.println(companyId);
        if (DbDVCompanyAnalysis.isTableTimeRecordBranchDVCA(branchId, companyId) != true) {
            DbDVCompanyAnalysis.AddRecordBranchDVCA(branchId, companyId); // byWhat is byDefault (month).
        } else {
            return DbDVCompanyAnalysis.UpdateRecordBranchDVCAIncrement(branchId, companyId, sales, income, clientIn, invShortage, discountByUser, damagedProducts, returnPurchases, shiftEndsEarly);
        }

        return "Error in Update";

    }
}
