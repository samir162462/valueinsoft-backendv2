package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.Config.BillingProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class BillingSchedulerJobs {

    private final BillingSchedulerService billingSchedulerService;
    private final BillingProperties billingProperties;

    public BillingSchedulerJobs(BillingSchedulerService billingSchedulerService,
                                BillingProperties billingProperties) {
        this.billingSchedulerService = billingSchedulerService;
        this.billingProperties = billingProperties;
    }

    @Scheduled(cron = "${vls.billing.renewal-scheduler-cron}")
    public void runRenewalScheduler() {
        if (!billingProperties.isRenewalSchedulerEnabled()) {
            return;
        }
        log.info("Running billing renewal scheduler");
        billingSchedulerService.runRenewalCycle();
    }

    @Scheduled(cron = "${vls.billing.dunning-scheduler-cron}")
    public void runDunningScheduler() {
        if (!billingProperties.isDunningSchedulerEnabled()) {
            return;
        }
        log.info("Running billing dunning scheduler");
        billingSchedulerService.runDunningCycle();
    }
}
