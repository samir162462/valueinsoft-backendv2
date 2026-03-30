package com.example.valueinsoftbackend.Controller.MainAppController;

/*
 * Copyright (c) Samir Filifl
 */


import lombok.extern.slf4j.Slf4j;

import com.example.valueinsoftbackend.Model.Branch;
import com.example.valueinsoftbackend.Model.Request.CreateSubscriptionRequest;
import com.example.valueinsoftbackend.Service.AuthorizationService;
import com.example.valueinsoftbackend.Service.BranchService;
import com.example.valueinsoftbackend.Service.SubscriptionService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.Positive;
import java.security.Principal;
import java.util.Map;

@RestController
@Validated
@RequestMapping("/appSubscription")
@Slf4j
public class AppSubscriptionController {

    private final SubscriptionService subscriptionService;
    private final BranchService branchService;
    private final AuthorizationService authorizationService;

    public AppSubscriptionController(SubscriptionService subscriptionService,
                                     BranchService branchService,
                                     AuthorizationService authorizationService) {
        this.subscriptionService = subscriptionService;
        this.branchService = branchService;
        this.authorizationService = authorizationService;
    }

    @RequestMapping(value = "/{branchId}", method = RequestMethod.GET)
    public Object appModelSubscriptionByBranchId(@PathVariable @Positive int branchId,
                                                 Principal principal) {
        Branch branch = branchService.getBranchById(branchId);
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                branch.getBranchOfCompanyId(),
                branchId,
                "company.settings.read"
        );
        return subscriptionService.getBranchSubscription(branchId);
    }

    @RequestMapping(value = "/AddSubscription", method = RequestMethod.POST)
    public String addSubscription(@Valid @RequestBody CreateSubscriptionRequest appModelSubscription,
                                  Principal principal) {
        Branch branch = branchService.getBranchById(appModelSubscription.getBranchId());
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                branch.getBranchOfCompanyId(),
                appModelSubscription.getBranchId(),
                "company.settings.edit"
        );
        return subscriptionService.addBranchSubscription(appModelSubscription);
    }

    @GetMapping(path = {"/Res"})
    public Map<String, String> payMobTransactionCallBack(@RequestParam(required = false) Map<String, String> qparams) {
        log.debug("Received PayMob redirect callback query params: {}", qparams.keySet());
        return qparams;
    }
}
