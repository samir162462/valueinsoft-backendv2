package com.example.valueinsoftbackend.companyinsights.config;

import java.math.BigDecimal;

/**
 * Resolved, immutable per-company thresholds (company_insight_settings row or defaults).
 */
public record CompanyInsightThresholds(
        long companyId,
        BigDecimal lowPerformingBranchDeviationPct,
        int criticalStockCoverageDays,
        int lowStockMultiBranchCount,
        int deadStockNoSaleDays,
        BigDecimal deadStockMinValue,
        BigDecimal marginDropPct,
        BigDecimal materialSalesDropPct,
        int noSalesAlertDelayMinutes,
        int minHistoryDaysForComparison,
        int newBranchGraceDays,
        int maxActiveInsightsPerCategory,
        int insightCooldownHours,
        String currencyCode,
        String timezone,
        boolean aiEnrichmentEnabled
) {

    public static CompanyInsightThresholds defaults(long companyId, String currencyCode, String timezone) {
        return new CompanyInsightThresholds(
                companyId,
                new BigDecimal("25.00"),
                7,
                2,
                60,
                new BigDecimal("1000"),
                new BigDecimal("5.00"),
                new BigDecimal("15.00"),
                180,
                28,
                30,
                5,
                72,
                currencyCode == null || currencyCode.isBlank() ? "EGP" : currencyCode,
                timezone == null || timezone.isBlank() ? "Africa/Cairo" : timezone,
                true
        );
    }
}
