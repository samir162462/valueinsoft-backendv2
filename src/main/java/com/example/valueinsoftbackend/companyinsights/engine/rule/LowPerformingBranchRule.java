package com.example.valueinsoftbackend.companyinsights.engine.rule;

import com.example.valueinsoftbackend.DatabaseRequests.DbBranch;
import com.example.valueinsoftbackend.Model.Branch;
import com.example.valueinsoftbackend.companyinsights.ai.CompanyInsightTemplateRenderer;
import com.example.valueinsoftbackend.companyinsights.config.AllowedActionCode;
import com.example.valueinsoftbackend.companyinsights.config.CompanyInsightThresholds;
import com.example.valueinsoftbackend.companyinsights.config.InsightRole;
import com.example.valueinsoftbackend.companyinsights.config.InsightType;
import com.example.valueinsoftbackend.companyinsights.config.Severity;
import com.example.valueinsoftbackend.companyinsights.engine.CompanyInsightRule;
import com.example.valueinsoftbackend.companyinsights.engine.InsightCandidate;
import com.example.valueinsoftbackend.companyinsights.kpi.CompanyKpiRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LOW_PERFORMING_BRANCH — detects active branches materially below both the company branch
 * average and their own prior-week baseline. Excludes inactive branches, branches with
 * incomplete data, and branches without a prior-week baseline (covers new branches).
 */
@Component
public class LowPerformingBranchRule implements CompanyInsightRule {

    private final CompanyKpiRepository kpiRepository;
    private final CompanyInsightTemplateRenderer renderer;
    private final DbBranch dbBranch;

    public LowPerformingBranchRule(CompanyKpiRepository kpiRepository,
                                   CompanyInsightTemplateRenderer renderer,
                                   DbBranch dbBranch) {
        this.kpiRepository = kpiRepository;
        this.renderer = renderer;
        this.dbBranch = dbBranch;
    }

    @Override
    public List<InsightCandidate> evaluate(RuleContext context) {
        CompanyInsightThresholds t = context.thresholds();
        LocalDate weekStart = context.asOfDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY));
        LocalDate weekEnd = weekStart.plusDays(6);
        LocalDate prevStart = weekStart.minusWeeks(1);
        LocalDate prevEnd = prevStart.plusDays(6);

        List<CompanyKpiRepository.BranchDailyKpiRow> rows =
                kpiRepository.findBranchDailyRange(context.companyId(), prevStart, weekEnd);
        if (rows.isEmpty()) {
            return List.of();
        }

        Map<Long, BranchAgg> byBranch = new HashMap<>();
        for (CompanyKpiRepository.BranchDailyKpiRow row : rows) {
            BranchAgg agg = byBranch.computeIfAbsent(row.branchId(), BranchAgg::new);
            boolean inCurrent = !row.businessDate().isBefore(weekStart) && !row.businessDate().isAfter(weekEnd);
            if (inCurrent) {
                agg.currentSales += row.salesAmount();
                agg.currentDays++;
                if (!"COMPLETE".equals(row.dataQualityStatus())) {
                    agg.currentComplete = false;
                }
                if (row.branchActive()) {
                    agg.active = true;
                }
            } else {
                agg.previousSales += row.salesAmount();
                agg.previousDays++;
            }
        }

        Map<Long, String> branchNames = branchNames(Math.toIntExact(context.companyId()));

        // Company branch average over eligible branches (active, complete, with baseline).
        double sumEligible = 0;
        int countEligible = 0;
        for (BranchAgg agg : byBranch.values()) {
            if (agg.eligible()) {
                sumEligible += agg.currentSales;
                countEligible++;
            }
        }
        if (countEligible < 2) {
            return List.of(); // need at least a couple of comparable branches
        }
        double companyAverage = sumEligible / countEligible;
        if (companyAverage <= 0) {
            return List.of();
        }

        double deviation = t.lowPerformingBranchDeviationPct().doubleValue();
        List<InsightCandidate> candidates = new ArrayList<>();
        for (BranchAgg agg : byBranch.values()) {
            if (!agg.eligible()) {
                continue;
            }
            double gapVsAverage = round1((companyAverage - agg.currentSales) / companyAverage * 100.0);
            double gapVsBaseline = round1((agg.previousSales - agg.currentSales) / agg.previousSales * 100.0);
            if (gapVsAverage < deviation || gapVsBaseline < deviation) {
                continue;
            }

            String branchName = branchNames.getOrDefault(agg.branchId, "Branch #" + agg.branchId);
            Severity severity = (gapVsAverage >= 2 * deviation || gapVsBaseline >= 2 * deviation)
                    ? Severity.CRITICAL : Severity.WARNING;

            CompanyInsightTemplateRenderer.Rendered rendered =
                    renderer.lowPerformingBranch(branchName, gapVsAverage, gapVsBaseline);

            Map<String, Object> slots = new LinkedHashMap<>();
            slots.put("branchName", branchName);
            slots.put("gapVsAveragePercent", gapVsAverage);
            slots.put("gapVsBaselinePercent", gapVsBaseline);
            slots.put("currentSales", round2(agg.currentSales));
            slots.put("companyAverageSales", round2(companyAverage));

            Map<String, Object> sourceMetrics = new LinkedHashMap<>();
            sourceMetrics.put("weekStart", weekStart.toString());
            sourceMetrics.put("weekEnd", weekEnd.toString());
            sourceMetrics.put("currentSales", round2(agg.currentSales));
            sourceMetrics.put("previousSales", round2(agg.previousSales));
            sourceMetrics.put("companyAverage", round2(companyAverage));

            Map<String, Object> actionContext = new LinkedHashMap<>();
            actionContext.put("branchId", agg.branchId);
            actionContext.put("from", weekStart.toString());
            actionContext.put("to", weekEnd.toString());

            int priority = (severity == Severity.CRITICAL ? 300 : 200)
                    + (int) Math.min(99, Math.round(Math.max(gapVsAverage, gapVsBaseline)));

            candidates.add(new InsightCandidate(
                    context.companyId(),
                    InsightType.LOW_PERFORMING_BRANCH,
                    "LOWPERF|" + context.companyId() + "|" + agg.branchId + "|" + weekStart,
                    "WEEK",
                    weekStart,
                    weekEnd,
                    severity,
                    priority,
                    InsightRole.PRIMARY,
                    null,
                    rendered.title(),
                    rendered.description(),
                    rendered.summary(),
                    rendered.localized(),
                    slots,
                    BigDecimal.valueOf(round2(companyAverage - agg.currentSales)),
                    List.of(agg.branchId),
                    List.of(),
                    null,
                    AllowedActionCode.OPEN_BRANCH_PERFORMANCE_REPORT,
                    actionContext,
                    sourceMetrics,
                    "COMPLETE",
                    null
            ));
        }
        return candidates;
    }

    private Map<Long, String> branchNames(int companyId) {
        Map<Long, String> names = new HashMap<>();
        try {
            for (Branch branch : dbBranch.getBranchByCompanyId(companyId)) {
                names.put((long) branch.getBranchID(), branch.getBranchName());
            }
        } catch (RuntimeException ignored) {
            // fall back to ids
        }
        return names;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static final class BranchAgg {
        final long branchId;
        double currentSales;
        double previousSales;
        int currentDays;
        int previousDays;
        boolean currentComplete = true;
        boolean active;

        BranchAgg(long branchId) {
            this.branchId = branchId;
        }

        boolean eligible() {
            return active && currentComplete && previousDays > 0 && previousSales > 0 && currentDays > 0;
        }
    }
}
