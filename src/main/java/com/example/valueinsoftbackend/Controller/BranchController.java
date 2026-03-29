package com.example.valueinsoftbackend.Controller;

import java.util.ArrayList;
import java.util.Map;

import javax.validation.constraints.Positive;

import com.example.valueinsoftbackend.Service.CompanyService;
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
import com.example.valueinsoftbackend.Model.Request.CreateBranchRequest;
import com.example.valueinsoftbackend.Service.BranchService;
import com.example.valueinsoftbackend.Service.SubscriptionService;

import javax.validation.Valid;

@RestController
@Validated
@RequestMapping("/Branch")
public class BranchController {


    private final CompanyService companyService;
    private final BranchService branchService;
    private final SubscriptionService subscriptionService;


    @Autowired
    public BranchController(CompanyService companyService, BranchService branchService, SubscriptionService subscriptionService) {
        this.companyService = companyService;
        this.branchService = branchService;
        this.subscriptionService = subscriptionService;
    }



    @RequestMapping(value = "/getBranchById", method = RequestMethod.GET)
    @ResponseBody
    public Company getCompanyById(
            @RequestParam("id") @Positive int id
    ) {
        return companyService.getCompanyById(id);
    }

    @RequestMapping(value = "{id}/getBranchesByCompanyId", method = RequestMethod.GET)
    @ResponseBody
    public ArrayList<Branch> getBranchesByCompanyId(
            @PathVariable("id") @Positive int id
    ) {
        return branchService.getBranchesByCompanyId(id);
    }


    @PostMapping("/AddBranch")
    public ResponseEntity<Object> newUser(@Valid @RequestBody CreateBranchRequest body) {
        int branchId = branchService.createBranch(body);
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
