package com.example.valueinsoftbackend.companyinsights.scheduler;

import com.example.valueinsoftbackend.companyinsights.config.CompanyInsightProperties;
import com.example.valueinsoftbackend.companyinsights.config.CompanyInsightThresholds;
import com.example.valueinsoftbackend.companyinsights.dirty.InsightDirtyQueueService;
import com.example.valueinsoftbackend.companyinsights.engine.CompanyInsightEngineService;
import com.example.valueinsoftbackend.companyinsights.kpi.CompanyInventorySnapshotRepository;
import com.example.valueinsoftbackend.companyinsights.lifecycle.CompanyInsightSettingsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Drains the dirty queue and refreshes inventory insights only for companies whose stock
 * changed (debounced). Rebuilds that company's inventory snapshot for today, then re-runs
 * the deterministic engine. Never touches companies with no stock movement.
 */
@Component
@Slf4j
public class CompanyStockInsightJob {

    private static final String JOB_NAME = "company-stock-insights";
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Africa/Cairo");

    private final InsightDirtyQueueService dirtyQueueService;
    private final CompanyInventorySnapshotRepository inventorySnapshotRepository;
    private final CompanyInsightEngineService engineService;
    private final CompanyInsightSettingsService settingsService;
    private final CompanyInsightJobLockManager lockManager;
    private final CompanyInsightProperties properties;

    public CompanyStockInsightJob(InsightDirtyQueueService dirtyQueueService,
                                  CompanyInventorySnapshotRepository inventorySnapshotRepository,
                                  CompanyInsightEngineService engineService,
                                  CompanyInsightSettingsService settingsService,
                                  CompanyInsightJobLockManager lockManager,
                                  CompanyInsightProperties properties) {
        this.dirtyQueueService = dirtyQueueService;
        this.inventorySnapshotRepository = inventorySnapshotRepository;
        this.engineService = engineService;
        this.settingsService = settingsService;
        this.lockManager = lockManager;
        this.properties = properties;
    }

    @Scheduled(
            cron = "${vls.company-insights.stock.cron:0 */15 * * * *}",
            zone = "${vls.company-insights.stock.zone:Africa/Cairo}"
    )
    public void drainDirtyQueue() {
        if (!properties.isEnabled()) {
            return;
        }
        List<Long> companies = dirtyQueueService.readyCompanies();
        if (companies.isEmpty()) {
            return;
        }
        LocalDate today = LocalDate.now(DEFAULT_ZONE);
        for (Long companyIdLong : companies) {
            int companyId = Math.toIntExact(companyIdLong);
            try {
                lockManager.runExclusive(JOB_NAME, companyId, today, () -> {
                    CompanyInsightThresholds thresholds = settingsService.resolve(companyId);
                    inventorySnapshotRepository.rebuildSnapshot(companyId, today, thresholds.deadStockNoSaleDays());
                    int persisted = engineService.generateForCompany(companyId, today);
                    dirtyQueueService.markCompanyProcessed(companyId);
                    return persisted;
                });
            } catch (RuntimeException exception) {
                log.warn("Company stock insight refresh failed companyId={} reason={}", companyId, exception.getMessage());
            }
        }
    }
}
