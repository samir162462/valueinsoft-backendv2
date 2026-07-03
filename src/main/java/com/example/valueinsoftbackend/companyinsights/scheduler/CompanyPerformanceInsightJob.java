package com.example.valueinsoftbackend.companyinsights.scheduler;

import com.example.valueinsoftbackend.DatabaseRequests.DbCompany;
import com.example.valueinsoftbackend.Model.Company;
import com.example.valueinsoftbackend.companyinsights.config.CompanyInsightProperties;
import com.example.valueinsoftbackend.companyinsights.engine.CompanyInsightEngineService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Daily job that runs the deterministic company-insight engine (weekly performance,
 * low-performing branch, etc.) over the trusted KPI snapshots. Runs after the KPI
 * aggregation job, lock-guarded per company. Never on the dashboard read path.
 */
@Component
@Slf4j
public class CompanyPerformanceInsightJob {

    private static final String JOB_NAME = "company-performance-insights";
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Africa/Cairo");

    private final DbCompany dbCompany;
    private final CompanyInsightEngineService engineService;
    private final CompanyInsightJobLockManager lockManager;
    private final CompanyInsightProperties properties;

    public CompanyPerformanceInsightJob(DbCompany dbCompany,
                                        CompanyInsightEngineService engineService,
                                        CompanyInsightJobLockManager lockManager,
                                        CompanyInsightProperties properties) {
        this.dbCompany = dbCompany;
        this.engineService = engineService;
        this.lockManager = lockManager;
        this.properties = properties;
    }

    @Scheduled(
            cron = "${vls.company-insights.performance.cron:0 30 3 * * *}",
            zone = "${vls.company-insights.performance.zone:Africa/Cairo}"
    )
    public void runDailyInsightGeneration() {
        if (!properties.isEnabled()) {
            return;
        }
        LocalDate asOfDate = LocalDate.now(DEFAULT_ZONE).minusDays(1);
        List<Company> companies = dbCompany.getAllCompanies();
        log.info("Company performance insight job starting asOf={} companies={}", asOfDate, companies.size());

        for (Company company : companies) {
            int companyId = company.getCompanyId();
            if (companyId <= 0) {
                continue;
            }
            try {
                lockManager.runExclusive(
                        JOB_NAME,
                        companyId,
                        asOfDate,
                        () -> engineService.generateForCompany(companyId, asOfDate)
                );
            } catch (RuntimeException exception) {
                log.warn("Company performance insights failed companyId={} reason={}", companyId, exception.getMessage());
            }
        }
        log.info("Company performance insight job finished asOf={}", asOfDate);
    }
}
