/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.Controller.MainAppController;

import com.example.valueinsoftbackend.Model.Request.CreateSubscriptionRequest;
import com.example.valueinsoftbackend.Service.SubscriptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.Positive;
import java.util.Map;

@RestController
@Validated
@RequestMapping("/appSubscription")
public class AppSubscriptionController {

    private static final Logger log = LoggerFactory.getLogger(AppSubscriptionController.class);

    private final SubscriptionService subscriptionService;

    public AppSubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @RequestMapping(value = "/{branchId}", method = RequestMethod.GET)
    public Object appModelSubscriptionByBranchId(@PathVariable @Positive int branchId) {
        return subscriptionService.getBranchSubscription(branchId);
    }

    @RequestMapping(value = "/AddSubscription", method = RequestMethod.POST)
    public String addSubscription(@Valid @RequestBody CreateSubscriptionRequest appModelSubscription) {
        return subscriptionService.addBranchSubscription(appModelSubscription);
    }

    @GetMapping(path = {"/Res"})
    public Map<String, String> payMobTransactionCallBack(@RequestParam(required = false) Map<String, String> qparams) {
        log.debug("Received PayMob redirect callback query params: {}", qparams.keySet());
        return qparams;
    }
}
