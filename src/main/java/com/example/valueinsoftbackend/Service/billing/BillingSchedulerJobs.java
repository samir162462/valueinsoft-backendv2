package com.example.valueinsoftbackend.Service.billing;

import com.example.valueinsoftbackend.Config.BillingProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@ConditionalOnExpression(
        "'${vls.billing.renewal-scheduler-enabled:false}' == 'true' || '${vls.billing.dunning-scheduler-enabled:false}' == 'true'"
)
public class BillingSchedulerJobs {

    private final BillingSchedulerService billingSchedulerService;
    private final BillingProperties billingProperties;
    private final AiUsageBillingService aiUsageBillingService;
    private final com.example.valueinsoftbackend.ai.config.AiProperties aiProperties;

    public BillingSchedulerJobs(BillingSchedulerService billingSchedulerService,
                                BillingProperties billingProperties,
                                AiUsageBillingService aiUsageBillingService,
                                com.example.valueinsoftbackend.ai.config.AiProperties aiProperties) {
        this.billingSchedulerService = billingSchedulerService;
        this.billingProperties = billingProperties;
        this.aiUsageBillingService = aiUsageBillingService;
        this.aiProperties = aiProperties;
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

    /** Bills last month's metered AI usage on the 1st of every month at 03:30. */
    @Scheduled(cron = "${vls.billing.ai-usage-billing-cron:0 30 3 1 * *}")
    public void runAiUsageBillingScheduler() {
        if (aiProperties.getUsageBilling() == null || !aiProperties.getUsageBilling().isEnabled()) {
            return;
        }
        log.info("Running monthly AI usage billing scheduler");
        aiUsageBillingService.runMonthlyAiUsageBilling(java.time.YearMonth.now().minusMonths(1), null);
    }
}
