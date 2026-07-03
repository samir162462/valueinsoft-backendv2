package com.example.valueinsoftbackend.companyinsights.scheduler;

import com.example.valueinsoftbackend.DatabaseRequests.DbCompany;
import com.example.valueinsoftbackend.Model.Company;
import com.example.valueinsoftbackend.companyinsights.activity.BranchNoActivityRule;
import com.example.valueinsoftbackend.companyinsights.config.CompanyInsightProperties;
import com.example.valueinsoftbackend.companyinsights.config.CompanyInsightThresholds;
import com.example.valueinsoftbackend.companyinsights.engine.InsightCandidate;
import com.example.valueinsoftbackend.companyinsights.lifecycle.CompanyInsightRepository;
import com.example.valueinsoftbackend.companyinsights.lifecycle.CompanyInsightSettingsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Hourly job that evaluates BRANCH_NO_ACTIVITY using live activity data (during/after
 * business hours). Lock-guarded per company; short TTL because it is a same-day signal.
 */
@Component
@Slf4j
public class BranchNoActivityJob {

    private static final String JOB_NAME = "branch-no-activity";
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Africa/Cairo");
    private static final int NO_ACTIVITY_TTL_DAYS = 1;

    private final DbCompany dbCompany;
    private final BranchNoActivityRule rule;
    private final CompanyInsightSettingsService settingsService;
    private final CompanyInsightRepository insightRepository;
    private final CompanyInsightJobLockManager lockManager;
    private final CompanyInsightProperties properties;

    public BranchNoActivityJob(DbCompany dbCompany,
                               BranchNoActivityRule rule,
                               CompanyInsightSettingsService settingsService,
                               CompanyInsightRepository insightRepository,
                               CompanyInsightJobLockManager lockManager,
                               CompanyInsightProperties properties) {
        this.dbCompany = dbCompany;
        this.rule = rule;
        this.settingsService = settingsService;
        this.insightRepository = insightRepository;
        this.lockManager = lockManager;
        this.properties = properties;
    }

    @Scheduled(
            cron = "${vls.company-insights.no-activity.cron:0 0 * * * *}",
            zone = "${vls.company-insights.no-activity.zone:Africa/Cairo}"
    )
    public void runHourly() {
        if (!properties.isEnabled()) {
            return;
        }
        LocalDate today = LocalDate.now(DEFAULT_ZONE);
        List<Company> companies = dbCompany.getAllCompanies();
        for (Company company : companies) {
            int companyId = company.getCompanyId();
            if (companyId <= 0) {
                continue;
            }
            try {
                lockManager.runExclusive(JOB_NAME, companyId, today, () -> {
                    CompanyInsightThresholds thresholds = settingsService.resolve(companyId);
                    List<InsightCandidate> candidates = rule.evaluate(companyId, thresholds);
                    int persisted = 0;
                    for (InsightCandidate candidate : candidates) {
                        persisted += insightRepository.upsertDetected(candidate, NO_ACTIVITY_TTL_DAYS);
                    }
                    return persisted;
                });
            } catch (RuntimeException exception) {
                log.warn("Branch no-activity job failed companyId={} reason={}", companyId, exception.getMessage());
            }
        }
    }
}
