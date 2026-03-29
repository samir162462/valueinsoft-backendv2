package com.example.valueinsoftbackend.Controller;

import java.util.ArrayList;
import java.util.Map;

import javax.validation.constraints.Positive;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.valueinsoftbackend.DatabaseRequests.DbBranch;
import com.example.valueinsoftbackend.DatabaseRequests.DbCompany;
import com.example.valueinsoftbackend.Model.Branch;
import com.example.valueinsoftbackend.Model.Company;
import com.example.valueinsoftbackend.Service.SubscriptionService;

@RestController
@Validated
@RequestMapping("/Branch")
public class BranchController {


    private final DbCompany dbCompany;
    private final DbBranch dbBranch;
    private final SubscriptionService subscriptionService;


    @Autowired
    public BranchController(DbCompany dbCompany, DbBranch dbBranch, SubscriptionService subscriptionService) {
        this.dbCompany = dbCompany;
        this.dbBranch = dbBranch;
        this.subscriptionService = subscriptionService;
    }



    @RequestMapping(value = "/getBranchById", method = RequestMethod.GET)
    @ResponseBody
    public Company getCompanyById(
            @RequestParam("id") String id
    ) {
        return dbCompany.getCompanyById(id);
    }

    @RequestMapping(value = "{id}/getBranchesByCompanyId", method = RequestMethod.GET)
    @ResponseBody
    public ArrayList<Branch> getBranchesByCompanyId(
            @PathVariable("id") int id
    ) {
        return (ArrayList<Branch>) dbBranch.getBranchByCompanyId(id);
    }


    @PostMapping("/AddBranch")
    public ResponseEntity<Object> newUser(@RequestBody Map<String, Object> body) {
        int branchId = -1;
        int companyId = (int) body.get("CompanyId");
        String branchName = body.get("branchName").toString();
        String branchLocation = body.get("branchLocation").toString();

        try {

            dbBranch.addBranch(branchName, branchLocation, companyId);
            branchId = dbBranch.getBranchIdByCompanyNameAndBranchName(companyId, branchName);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).body("{\"title\" : \"the Branched added saved\", \"branchId\" : " + branchId + "}");

    }

    @RequestMapping(value = "/isActive/{branchId}", method = RequestMethod.GET)
    @ResponseBody
    public ResponseEntity<Object> isActiveBranch(
            @PathVariable("branchId") @Positive int branchId
    ) {

        Map<String, Object> Details = subscriptionService.isActive(branchId);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Details);
    }




}
