package com.example.valueinsoftbackend.companyinsights.scheduler;

import com.example.valueinsoftbackend.DatabaseRequests.DbCompany;
import com.example.valueinsoftbackend.Model.Company;
import com.example.valueinsoftbackend.companyinsights.config.CompanyInsightProperties;
import com.example.valueinsoftbackend.companyinsights.kpi.CompanyKpiAggregationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Daily job that rebuilds trusted company KPI snapshots for the previous business day
 * (plus a trailing re-close window for late orders/returns). Idempotent and lock-guarded
 * per company. Never invoked on the dashboard read path.
 */
@Component
@Slf4j
public class CompanyKpiAggregationJob {

    private static final String JOB_NAME = "company-kpi-aggregation";
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Africa/Cairo");

    private final DbCompany dbCompany;
    private final CompanyKpiAggregationService aggregationService;
    private final CompanyInsightJobLockManager lockManager;
    private final CompanyInsightProperties properties;

    public CompanyKpiAggregationJob(DbCompany dbCompany,
                                    CompanyKpiAggregationService aggregationService,
                                    CompanyInsightJobLockManager lockManager,
                                    CompanyInsightProperties properties) {
        this.dbCompany = dbCompany;
        this.aggregationService = aggregationService;
        this.lockManager = lockManager;
        this.properties = properties;
    }

    @Scheduled(
            cron = "${vls.company-insights.aggregation.cron:0 30 2 * * *}",
            zone = "${vls.company-insights.aggregation.zone:Africa/Cairo}"
    )
    public void runDailyAggregation() {
        if (!properties.isEnabled() || !properties.isScheduledJobsEnabled()) {
            return;
        }
        LocalDate targetDate = LocalDate.now(DEFAULT_ZONE).minusDays(1);
        List<Company> companies = dbCompany.getAllCompanies();
        log.info("Company KPI aggregation job starting date={} companies={}", targetDate, companies.size());

        int succeeded = 0;
        for (Company company : companies) {
            int companyId = company.getCompanyId();
            if (companyId <= 0) {
                continue;
            }
            try {
                boolean executed = lockManager.runExclusive(
                        JOB_NAME,
                        companyId,
                        targetDate,
                        () -> aggregationService.aggregateWithTrailingWindow(companyId, targetDate)
                );
                if (executed) {
                    succeeded++;
                }
            } catch (RuntimeException exception) {
                // Failure for one company must not stop the others; already logged + ledgered.
                log.warn("Company KPI aggregation failed companyId={} reason={}", companyId, exception.getMessage());
            }
        }
        log.info("Company KPI aggregation job finished date={} succeeded={}", targetDate, succeeded);
    }
}
