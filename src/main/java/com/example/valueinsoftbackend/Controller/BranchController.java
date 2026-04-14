package com.example.valueinsoftbackend.Controller;

import com.example.valueinsoftbackend.Model.Branch;
import com.example.valueinsoftbackend.Model.BranchSettings.BranchSettingsBundleResponse;
import com.example.valueinsoftbackend.Model.Company;
import com.example.valueinsoftbackend.Model.Request.BranchSettings.BranchSettingsBatchUpdateRequest;
import com.example.valueinsoftbackend.Model.Request.CreateBranchRequest;
import com.example.valueinsoftbackend.Service.AuthorizationService;
import com.example.valueinsoftbackend.Service.BranchService;
import com.example.valueinsoftbackend.Service.BranchSettingsService;
import com.example.valueinsoftbackend.Service.CompanyService;
import com.example.valueinsoftbackend.Service.SubscriptionService;
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

import javax.validation.Valid;
import javax.validation.constraints.Positive;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Map;

@RestController
@Validated
@RequestMapping("/Branch")
public class BranchController {

    private final CompanyService companyService;
    private final BranchService branchService;
    private final SubscriptionService subscriptionService;
    private final AuthorizationService authorizationService;
    private final BranchSettingsService branchSettingsService;

    @Autowired
    public BranchController(CompanyService companyService,
                            BranchService branchService,
                            SubscriptionService subscriptionService,
                            AuthorizationService authorizationService,
                            BranchSettingsService branchSettingsService) {
        this.companyService = companyService;
        this.branchService = branchService;
        this.subscriptionService = subscriptionService;
        this.authorizationService = authorizationService;
        this.branchSettingsService = branchSettingsService;
    }

    @RequestMapping(value = "/getBranchById", method = RequestMethod.GET)
    @ResponseBody
    public Company getCompanyById(@RequestParam("id") @Positive int id,
                                  Principal principal) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                id,
                null,
                "company.settings.read"
        );
        return companyService.getCompanyById(id);
    }

    @RequestMapping(value = "{id}/getBranchesByCompanyId", method = RequestMethod.GET)
    @ResponseBody
    public ArrayList<Branch> getBranchesByCompanyId(@PathVariable("id") @Positive int id,
                                                    Principal principal) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                id,
                null,
                "company.settings.read"
        );
        return branchService.getBranchesByCompanyId(id);
    }

    @PostMapping("/AddBranch")
    public ResponseEntity<Object> newUser(@Valid @RequestBody CreateBranchRequest body,
                                          Principal principal) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                body.getCompanyId(),
                null,
                "company.settings.edit"
        );
        int branchId = branchService.createBranch(body);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body("{\"title\" : \"the Branched added saved\", \"branchId\" : " + branchId + "}");
    }

    @RequestMapping(value = "/isActive/{branchId}", method = RequestMethod.GET)
    @ResponseBody
    public ResponseEntity<Object> isActiveBranch(@PathVariable("branchId") @Positive int branchId,
                                                 Principal principal) {
        Branch branch = branchService.getBranchById(branchId);
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                branch.getBranchOfCompanyId(),
                branchId,
                "company.settings.read"
        );

        Map<String, Object> details = subscriptionService.isActive(branchId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(details);
    }

    @RequestMapping(value = "/{tenantId}/{branchId}/settings", method = RequestMethod.GET)
    @ResponseBody
    public BranchSettingsBundleResponse getBranchSettings(@PathVariable("tenantId") @Positive int tenantId,
                                                          @PathVariable("branchId") @Positive int branchId,
                                                          Principal principal) {
        return branchSettingsService.getBranchSettingsForAuthenticatedUser(principal.getName(), tenantId, branchId);
    }

    @RequestMapping(value = "/{tenantId}/{branchId}/settings", method = RequestMethod.PUT)
    @ResponseBody
    public BranchSettingsBundleResponse saveBranchSettings(@PathVariable("tenantId") @Positive int tenantId,
                                                           @PathVariable("branchId") @Positive int branchId,
                                                           @Valid @RequestBody BranchSettingsBatchUpdateRequest request,
                                                           Principal principal) {
        return branchSettingsService.saveBranchSettingsForAuthenticatedUser(principal.getName(), tenantId, branchId, request);
    }
}
