package com.example.valueinsoftbackend.companyinsights.activity;

import com.example.valueinsoftbackend.DatabaseRequests.DbBranch;
import com.example.valueinsoftbackend.DatabaseRequests.DbBranchSettings;
import com.example.valueinsoftbackend.Model.Branch;
import com.example.valueinsoftbackend.companyinsights.ai.CompanyInsightTemplateRenderer;
import com.example.valueinsoftbackend.companyinsights.config.AllowedActionCode;
import com.example.valueinsoftbackend.companyinsights.config.CompanyInsightThresholds;
import com.example.valueinsoftbackend.companyinsights.config.InsightRole;
import com.example.valueinsoftbackend.companyinsights.config.InsightType;
import com.example.valueinsoftbackend.companyinsights.config.Severity;
import com.example.valueinsoftbackend.companyinsights.engine.InsightCandidate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BRANCH_NO_ACTIVITY — detects active branches with zero orders after enough of today's
 * business hours have elapsed. Uses live activity data. Intentionally NOT a
 * {@code CompanyInsightRule} (so it is not run by the daily engine); the hourly
 * {@code BranchNoActivityJob} invokes it directly.
 *
 * <p>Suppression: no alert before the configured delay after opening; no alert if the live
 * activity read failed (unhealthy/incomplete data); no alert if the branch is marked inactive.
 */
@Component
public class BranchNoActivityRule {

    private static final LocalTime DEFAULT_OPEN_TIME = LocalTime.of(10, 0);
    private static final String[] OPEN_TIME_KEYS = {
            "branch.businessHours.open", "pos.businessOpenTime", "branch.openTime"
    };
    private static final String[] ACTIVE_KEYS = { "branch.active", "branch.isActive" };

    private final DbBranch dbBranch;
    private final DbBranchSettings dbBranchSettings;
    private final LiveBranchActivityReader activityReader;
    private final CompanyInsightTemplateRenderer renderer;

    public BranchNoActivityRule(DbBranch dbBranch,
                                DbBranchSettings dbBranchSettings,
                                LiveBranchActivityReader activityReader,
                                CompanyInsightTemplateRenderer renderer) {
        this.dbBranch = dbBranch;
        this.dbBranchSettings = dbBranchSettings;
        this.activityReader = activityReader;
        this.renderer = renderer;
    }

    public List<InsightCandidate> evaluate(int companyId, CompanyInsightThresholds thresholds) {
        ZoneId zone = zoneOf(thresholds.timezone());
        ZonedDateTime now = ZonedDateTime.now(zone);
        LocalDate today = now.toLocalDate();
        int delayMinutes = thresholds.noSalesAlertDelayMinutes();

        List<InsightCandidate> candidates = new ArrayList<>();
        for (Branch branch : dbBranch.getBranchByCompanyId(companyId)) {
            int branchId = branch.getBranchID();
            Map<String, Object> settings = safeSettings(companyId, branchId);
            if (!isActive(settings)) {
                continue;
            }
            LocalTime openTime = resolveOpenTime(settings);
            int minutesSinceOpen = (int) java.time.Duration.between(openTime, now.toLocalTime()).toMinutes();
            if (minutesSinceOpen < delayMinutes) {
                continue; // before business-hours threshold -> suppress
            }

            int orders = activityReader.todayOrderCount(companyId, branchId, today);
            if (orders < 0) {
                continue; // data unhealthy/incomplete -> suppress
            }
            if (orders > 0) {
                continue; // there is activity
            }

            String branchName = branch.getBranchName() == null ? ("Branch #" + branchId) : branch.getBranchName();
            Severity severity = minutesSinceOpen >= delayMinutes * 3 ? Severity.CRITICAL : Severity.WARNING;
            CompanyInsightTemplateRenderer.Rendered rendered = renderer.branchNoActivity(branchName, minutesSinceOpen);

            Map<String, Object> slots = new LinkedHashMap<>();
            slots.put("branchName", branchName);
            slots.put("minutesSinceOpen", minutesSinceOpen);

            Map<String, Object> sourceMetrics = new LinkedHashMap<>();
            sourceMetrics.put("businessDate", today.toString());
            sourceMetrics.put("openTime", openTime.toString());
            sourceMetrics.put("ordersToday", orders);
            sourceMetrics.put("minutesSinceOpen", minutesSinceOpen);

            Map<String, Object> actionContext = new LinkedHashMap<>();
            actionContext.put("branchId", branchId);

            candidates.add(new InsightCandidate(
                    (long) companyId,
                    InsightType.BRANCH_NO_ACTIVITY,
                    "NOACTIVITY|" + companyId + "|" + branchId + "|" + today,
                    "DAY",
                    today,
                    today,
                    severity,
                    (severity == Severity.CRITICAL ? 300 : 200) + Math.min(99, minutesSinceOpen / 10),
                    InsightRole.PRIMARY,
                    null,
                    rendered.title(),
                    rendered.description(),
                    rendered.summary(),
                    rendered.localized(),
                    slots,
                    null,
                    List.of((long) branchId),
                    List.of(),
                    null,
                    AllowedActionCode.OPEN_BRANCH_POS,
                    actionContext,
                    sourceMetrics,
                    "COMPLETE",
                    null
            ));
        }
        return candidates;
    }

    private Map<String, Object> safeSettings(int companyId, int branchId) {
        try {
            return dbBranchSettings.getEffectiveValueMap(companyId, branchId);
        } catch (RuntimeException exception) {
            return Map.of();
        }
    }

    private boolean isActive(Map<String, Object> settings) {
        for (String key : ACTIVE_KEYS) {
            Object value = settings.get(key);
            if (value instanceof Boolean bool) {
                return bool;
            }
            if (value instanceof String text) {
                if ("false".equalsIgnoreCase(text.trim())) {
                    return false;
                }
                if ("true".equalsIgnoreCase(text.trim())) {
                    return true;
                }
            }
        }
        return true; // default active when no explicit setting
    }

    private LocalTime resolveOpenTime(Map<String, Object> settings) {
        for (String key : OPEN_TIME_KEYS) {
            Object value = settings.get(key);
            if (value instanceof String text && !text.isBlank()) {
                try {
                    return LocalTime.parse(text.trim());
                } catch (RuntimeException ignored) {
                    // try next key
                }
            }
        }
        return DEFAULT_OPEN_TIME;
    }

    private ZoneId zoneOf(String timezone) {
        try {
            return ZoneId.of(timezone == null || timezone.isBlank() ? "Africa/Cairo" : timezone);
        } catch (RuntimeException exception) {
            return ZoneId.of("Africa/Cairo");
        }
    }
}
