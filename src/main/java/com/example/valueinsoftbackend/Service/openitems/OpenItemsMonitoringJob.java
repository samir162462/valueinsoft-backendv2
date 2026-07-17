package com.example.valueinsoftbackend.Service.openitems;

import com.example.valueinsoftbackend.DatabaseRequests.DbOpenItemsReconciliation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically refreshes the per-tenant reconciliation drift gauges. */
@Component
@Slf4j
@ConditionalOnProperty(name = "finance.openitems.monitoring.enabled", havingValue = "true")
public class OpenItemsMonitoringJob {

    private final DbOpenItemsReconciliation repository;
    private final OpenItemsReconciliationService reconciliation;

    public OpenItemsMonitoringJob(DbOpenItemsReconciliation repository,
                                  OpenItemsReconciliationService reconciliation) {
        this.repository = repository;
        this.reconciliation = reconciliation;
    }

    @Scheduled(initialDelayString = "${finance.openitems.monitoring.initial-delay-ms:60000}",
            fixedDelayString = "${finance.openitems.monitoring.fixed-delay-ms:300000}")
    public void refreshDriftGauges() {
        for (Integer companyId : repository.companyIds()) {
            try {
                reconciliation.snapshot(companyId);
            } catch (RuntimeException exception) {
                log.error("Open-items reconciliation metric refresh failed for company {}", companyId, exception);
            }
        }
    }
}
