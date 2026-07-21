package com.example.valueinsoftbackend.companyinsights.scheduler;

import com.example.valueinsoftbackend.companyinsights.config.CompanyInsightProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily retention + expiry maintenance for Company Smart Insights.
 *
 * <p>Marks past-expiry active insights as EXPIRED and purges aged rows per the retention
 * policy. Idempotent deletes; guarded by a single advisory lock so only one instance runs.
 */
@Component
@Slf4j
public class CompanyInsightRetentionJob {

    private static final String JOB_NAME = "company-insight-retention";

    private final JdbcTemplate jdbcTemplate;
    private final CompanyInsightJobLockManager lockManager;
    private final CompanyInsightProperties properties;

    public CompanyInsightRetentionJob(JdbcTemplate jdbcTemplate,
                                      CompanyInsightJobLockManager lockManager,
                                      CompanyInsightProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.lockManager = lockManager;
        this.properties = properties;
    }

    @Scheduled(
            cron = "${vls.company-insights.retention.cron:0 45 4 * * *}",
            zone = "${vls.company-insights.retention.zone:Africa/Cairo}"
    )
    public void runDailyRetention() {
        if (!properties.isEnabled() || !properties.isScheduledJobsEnabled()) {
            return;
        }
        // company_id = 0 sentinel: this is a global (all-company) maintenance job.
        lockManager.runExclusive(JOB_NAME, 0, null, this::purge);
    }

    private int purge() {
        int affected = 0;

        // 1. Expire active insights past their expiry.
        affected += jdbcTemplate.update(
                "UPDATE public.ai_company_insight SET status = 'EXPIRED' " +
                        "WHERE status IN ('NEW','SEEN') AND expires_at IS NOT NULL AND expires_at < now()");

        // 2. Purge terminal insights older than 180 days.
        affected += jdbcTemplate.update(
                "DELETE FROM public.ai_company_insight " +
                        "WHERE status IN ('RESOLVED','DISMISSED','EXPIRED') AND updated_at < now() - interval '180 days'");

        // 3. Product-grain KPI: 90 days.
        affected += jdbcTemplate.update(
                "DELETE FROM public.branch_product_daily_kpi WHERE business_date < (CURRENT_DATE - 90)");

        // 4. Branch/company daily KPI: 13 months.
        affected += jdbcTemplate.update(
                "DELETE FROM public.branch_daily_kpi WHERE business_date < (CURRENT_DATE - interval '13 months')");
        affected += jdbcTemplate.update(
                "DELETE FROM public.company_daily_kpi WHERE business_date < (CURRENT_DATE - interval '13 months')");

        // 5. Inventory snapshot: 30 days.
        affected += jdbcTemplate.update(
                "DELETE FROM public.company_inventory_snapshot WHERE snapshot_date < (CURRENT_DATE - 30)");

        // 6. Processed dirty-queue rows: 7 days.
        affected += jdbcTemplate.update(
                "DELETE FROM public.company_insight_dirty_queue " +
                        "WHERE processed_at IS NOT NULL AND processed_at < now() - interval '7 days'");

        // 7. Job-run ledger: 30 days.
        affected += jdbcTemplate.update(
                "DELETE FROM public.company_insight_job_run WHERE started_at < now() - interval '30 days'");

        log.info("Company insight retention completed rowsAffected={}", affected);
        return affected;
    }
}
