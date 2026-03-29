package com.example.valueinsoftbackend.Controller;

import com.example.valueinsoftbackend.DatabaseRequests.DbCompany;
import com.example.valueinsoftbackend.DatabaseRequests.DbUsers;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Company;
import com.example.valueinsoftbackend.Model.Request.CreateCompanyRequest;
import com.example.valueinsoftbackend.Model.Request.UpdateCompanyImageRequest;
import com.example.valueinsoftbackend.Model.User;
import com.example.valueinsoftbackend.Service.CompanyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;


import java.util.ArrayList;
import java.util.Map;

import javax.validation.Valid;
import javax.validation.constraints.Positive;


@RestController
@Validated
@RequestMapping("/Company")

public class CompanyController {

    private static final Logger log = LoggerFactory.getLogger(CompanyController.class);

    private final DbCompany dbCompany;
    private final DbUsers dbUsers;
    private final CompanyService companyService;

    public CompanyController(DbCompany dbCompany, DbUsers dbUsers, CompanyService companyService) {
        this.dbCompany = dbCompany;
        this.dbUsers = dbUsers;
        this.companyService = companyService;
    }

    @RequestMapping(value = "/getCompany", method = RequestMethod.GET)
    @ResponseBody
    public Company getPersonsByNames(


            @RequestParam("id") String id

    ) {

        User u1 = dbUsers.getUser(id);
        if (u1 == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found");
        }

        return dbCompany.getCompanyByOwnerId(u1.getUserId());
    }


    @RequestMapping(value = "/getAllCompanies", method = RequestMethod.GET)
    @ResponseBody
    public ArrayList<Company> getAllCompanies(
    ) {

        return dbCompany.getAllCompanies();
    }



    //getCompanyAndBranchesByUserName
    @RequestMapping(value = "/getCompanyAndBranchesByUserName", method = RequestMethod.GET)
    @ResponseBody
    public Company CompanyAndBranchesByUserName(


            @RequestParam("id") String id

    ) {


        return dbCompany.getCompanyAndBranchesByUserName(id);
    }

    @RequestMapping(value = "/getCompanyById", method = RequestMethod.GET)
    @ResponseBody
    public Company getCompanyById(


            @RequestParam("id") String id

    ) {


        return dbCompany.getCompanyById(id);
    }

    @PostMapping("/saveCompany")
    public Company newCompany(@Valid @RequestBody CreateCompanyRequest request) {
        return companyService.createCompany(request);

    }

    @PutMapping("/updateImg/{companyId}")
    public ResponseEntity<String> updateImg(@PathVariable @Positive int companyId,
                                            @Valid @RequestBody UpdateCompanyImageRequest requestBody) {
        companyService.updateCompanyImage(companyId, requestBody.getImgFile());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body("Image Changed!");
    }


    @GetMapping("/listHeaders")
    public ResponseEntity<String> listAllHeaders(
            @RequestHeader Map<String, String> headers) {
        headers.forEach((key, value) -> log.debug("Header '{}' = {}", key, value));
        return new ResponseEntity<String>(
                String.format("Listed %d headers", headers.size()), HttpStatus.OK);
    }
}
