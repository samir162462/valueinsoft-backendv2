package com.example.valueinsoftbackend.companyinsights.api.dto;

import java.util.List;

/**
 * KPI header + alert counts + trend for the Company Smart Insights dashboard.
 */
public record CompanyInsightSummaryDto(
        String periodStart,
        String periodEnd,
        double totalSales,
        double totalGrossProfit,
        double grossMarginPct,
        long ordersCount,
        int branchCount,
        AlertCounts alerts,
        List<TrendPoint> trend
) {
    public record AlertCounts(long critical, long warning, long info, long total) {
    }

    public record TrendPoint(String date, double sales, double grossProfit, double grossMarginPct) {
    }
}
