package com.example.valueinsoftbackend.companyinsights.engine.rule;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * COMPANY_WEEKLY_PERFORMANCE — compares the current business week (Sat–Fri) vs the prior
 * week and fires on material regressions. PROFIT_MARGIN_DROP is folded in here as a
 * fire condition + contributing factors (no separate card), per the approved plan.
 */
@Component
public class CompanyWeeklyPerformanceRule implements CompanyInsightRule {

    private final CompanyKpiRepository kpiRepository;
    private final CompanyInsightTemplateRenderer renderer;

    public CompanyWeeklyPerformanceRule(CompanyKpiRepository kpiRepository,
                                        CompanyInsightTemplateRenderer renderer) {
        this.kpiRepository = kpiRepository;
        this.renderer = renderer;
    }

    @Override
    public List<InsightCandidate> evaluate(RuleContext context) {
        CompanyInsightThresholds t = context.thresholds();
        LocalDate weekStart = context.asOfDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY));
        LocalDate weekEnd = weekStart.plusDays(6);
        LocalDate prevStart = weekStart.minusWeeks(1);
        LocalDate prevEnd = prevStart.plusDays(6);

        List<CompanyKpiRepository.CompanyDailyKpiRow> rows =
                kpiRepository.findCompanyDailyRange(context.companyId(), prevStart, weekEnd);

        WeekAgg current = aggregate(rows, weekStart, weekEnd);
        WeekAgg previous = aggregate(rows, prevStart, prevEnd);

        // Data-quality gate: need a prior-week baseline with sales to compare against.
        if (previous.sales <= 0 && current.sales <= 0) {
            return List.of();
        }
        boolean baselineAvailable = previous.days > 0 && previous.sales > 0;
        if (!baselineAvailable) {
            return List.of(); // insufficient history -> no misleading comparison
        }

        double salesPct = pctChange(current.sales, previous.sales);
        double marginChange = round1(current.marginPct() - previous.marginPct());
        double ordersPct = pctChange(current.orders, previous.orders);
        double aovPct = pctChange(current.aov(), previous.aov());
        double discountPct = pctChange(current.discount, previous.discount);
        double returnPct = pctChange(current.returns, previous.returns);

        double materialSalesDrop = t.materialSalesDropPct().doubleValue();
        double marginDrop = t.marginDropPct().doubleValue();

        String condition;
        if (salesPct <= -materialSalesDrop) {
            condition = "SALES_DROP";
        } else if (salesPct > 0 && marginChange <= -marginDrop) {
            condition = "SALES_UP_MARGIN_DOWN";
        } else if (marginChange <= -marginDrop) {
            condition = "MARGIN_DROP";
        } else if (ordersPct > 0 && aovPct < 0) {
            condition = "ORDERS_UP_AOV_DOWN";
        } else {
            condition = "SUMMARY";
        }

        Severity severity;
        if (salesPct <= -2 * materialSalesDrop || marginChange <= -2 * marginDrop) {
            severity = Severity.CRITICAL;
        } else if (!"SUMMARY".equals(condition)) {
            severity = Severity.WARNING;
        } else {
            severity = Severity.INFO;
        }

        List<Map<String, Object>> contributing = new ArrayList<>();
        if (discountPct > 5.0) {
            contributing.add(factor("DISCOUNTS_UP", discountPct));
        }
        if (returnPct > 5.0) {
            contributing.add(factor("RETURNS_UP", returnPct));
        }

        CompanyInsightTemplateRenderer.Rendered rendered = renderer.weeklyPerformance(
                salesPct, marginChange, ordersPct, aovPct, discountPct, returnPct, condition);

        Map<String, Object> slots = new LinkedHashMap<>();
        slots.put("salesChangePercent", salesPct);
        slots.put("grossMarginChangePercent", marginChange);
        slots.put("ordersChangePercent", ordersPct);
        slots.put("aovChangePercent", aovPct);
        slots.put("discountChangePercent", discountPct);
        slots.put("returnChangePercent", returnPct);
        slots.put("currentSales", round2(current.sales));
        slots.put("previousSales", round2(previous.sales));
        slots.put("currentMarginPct", current.marginPct());
        slots.put("previousMarginPct", previous.marginPct());
        slots.put("primaryCondition", condition);

        Map<String, Object> sourceMetrics = new LinkedHashMap<>();
        sourceMetrics.put("weekStart", weekStart.toString());
        sourceMetrics.put("weekEnd", weekEnd.toString());
        sourceMetrics.put("current", current.toMap());
        sourceMetrics.put("previous", previous.toMap());

        Map<String, Object> actionContext = new LinkedHashMap<>();
        actionContext.put("from", weekStart.toString());
        actionContext.put("to", weekEnd.toString());
        actionContext.put("period", "THIS_WEEK");

        int priority = priorityScore(severity, salesPct, marginChange);
        BigDecimal financialImpact = BigDecimal.valueOf(round2(current.grossProfit - previous.grossProfit));

        InsightCandidate candidate = new InsightCandidate(
                context.companyId(),
                InsightType.COMPANY_WEEKLY_PERFORMANCE,
                "WEEKLY|" + context.companyId() + "|" + weekStart,
                "WEEK",
                weekStart,
                weekEnd,
                severity,
                priority,
                InsightRole.PRIMARY,
                "WEEKLY|" + context.companyId() + "|" + weekStart,
                rendered.title(),
                rendered.description(),
                rendered.summary(),
                rendered.localized(),
                slots,
                financialImpact,
                List.of(),
                List.of(),
                contributing.isEmpty() ? null : contributing,
                AllowedActionCode.OPEN_PROFIT_REPORT,
                actionContext,
                sourceMetrics,
                "COMPLETE",
                null
        );
        return List.of(candidate);
    }

    private Map<String, Object> factor(String code, double changePercent) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", code);
        map.put("changePercent", round1(changePercent));
        return map;
    }

    private int priorityScore(Severity severity, double salesPct, double marginChange) {
        int base = switch (severity) {
            case CRITICAL -> 300;
            case WARNING -> 200;
            case INFO -> 100;
        };
        int magnitude = (int) Math.min(99, Math.round(Math.max(Math.abs(salesPct), Math.abs(marginChange))));
        return base + magnitude;
    }

    private WeekAgg aggregate(List<CompanyKpiRepository.CompanyDailyKpiRow> rows, LocalDate from, LocalDate to) {
        WeekAgg agg = new WeekAgg();
        for (CompanyKpiRepository.CompanyDailyKpiRow row : rows) {
            LocalDate d = row.businessDate();
            if (d == null || d.isBefore(from) || d.isAfter(to)) {
                continue;
            }
            agg.days++;
            agg.sales += row.salesAmount();
            agg.grossProfit += row.grossProfitAmount();
            agg.orders += row.ordersCount();
            agg.discount += row.discountAmount();
            agg.returns += row.returnAmount();
        }
        return agg;
    }

    private static double pctChange(double current, double previous) {
        if (previous == 0) {
            return current > 0 ? 100.0 : 0.0;
        }
        return round1((current - previous) / previous * 100.0);
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static final class WeekAgg {
        int days;
        double sales;
        double grossProfit;
        long orders;
        double discount;
        double returns;

        double marginPct() {
            return sales > 0 ? Math.round(grossProfit / sales * 1000.0) / 10.0 : 0.0;
        }

        double aov() {
            return orders > 0 ? sales / orders : 0.0;
        }

        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("days", days);
            map.put("sales", round2(sales));
            map.put("grossProfit", round2(grossProfit));
            map.put("orders", orders);
            map.put("discount", round2(discount));
            map.put("returns", round2(returns));
            map.put("marginPct", marginPct());
            map.put("aov", round2(aov()));
            return map;
        }
    }
}
