package com.example.valueinsoftbackend.Controller;

import lombok.extern.slf4j.Slf4j;

import com.example.valueinsoftbackend.Model.Company;
import com.example.valueinsoftbackend.Model.Request.CreateCompanyRequest;
import com.example.valueinsoftbackend.Model.Request.UpdateCompanyImageRequest;
import com.example.valueinsoftbackend.Service.AuthorizationService;
import com.example.valueinsoftbackend.Service.CompanyService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;


import java.security.Principal;
import java.util.ArrayList;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;


@RestController
@Validated
@RequestMapping("/Company")

@Slf4j
public class CompanyController {

    private final CompanyService companyService;
    private final AuthorizationService authorizationService;

    public CompanyController(CompanyService companyService, AuthorizationService authorizationService) {
        this.companyService = companyService;
        this.authorizationService = authorizationService;
    }

    @RequestMapping(value = "/getCompany", method = RequestMethod.GET)
    @ResponseBody
    public Company getPersonsByNames(
            @RequestParam("id") String id

    ) {
        return companyService.getCompanyForOwnerUserName(id);
    }


    @RequestMapping(value = "/getAllCompanies", method = RequestMethod.GET)
    @ResponseBody
    public ArrayList<Company> getAllCompanies(
    ) {
        return companyService.getAllCompanies();
    }



    //getCompanyAndBranchesByUserName
    @RequestMapping(value = "/getCompanyAndBranchesByUserName", method = RequestMethod.GET)
    @ResponseBody
    public Company CompanyAndBranchesByUserName(
            @RequestParam("id") String id,
            Principal principal

    ) {
        authorizationService.assertSelfCapability(
                principal.getName(),
                id,
                "profile.self.read"
        );
        return companyService.getCompanyAndBranchesByUserName(id);
    }

    @RequestMapping(value = "/getCompanyById", method = RequestMethod.GET)
    @ResponseBody
    public Company getCompanyById(
            @RequestParam("id") @Positive int id

    ) {
        return companyService.getCompanyById(id);
    }

    @PostMapping("/saveCompany")
    public Company newCompany(@Valid @RequestBody CreateCompanyRequest request) {
        return companyService.createCompany(request);

    }

    @PutMapping("/updateImg/{companyId}")
    public ResponseEntity<String> updateImg(@PathVariable @Positive int companyId,
                                            Principal principal,
                                            @Valid @RequestBody UpdateCompanyImageRequest requestBody) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                null,
                "company.settings.edit"
        );
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
