package com.example.valueinsoftbackend.companyinsights.api.dto;

import com.example.valueinsoftbackend.companyinsights.config.CompanyInsightThresholds;

import java.math.BigDecimal;

/**
 * Editable per-company thresholds surfaced to the settings drawer.
 */
public record CompanyInsightSettingsDto(
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

    public static CompanyInsightSettingsDto from(CompanyInsightThresholds t) {
        return new CompanyInsightSettingsDto(
                t.lowPerformingBranchDeviationPct(),
                t.criticalStockCoverageDays(),
                t.lowStockMultiBranchCount(),
                t.deadStockNoSaleDays(),
                t.deadStockMinValue(),
                t.marginDropPct(),
                t.materialSalesDropPct(),
                t.noSalesAlertDelayMinutes(),
                t.minHistoryDaysForComparison(),
                t.newBranchGraceDays(),
                t.maxActiveInsightsPerCategory(),
                t.insightCooldownHours(),
                t.currencyCode(),
                t.timezone(),
                t.aiEnrichmentEnabled()
        );
    }

    public CompanyInsightThresholds toThresholds(long companyId) {
        return new CompanyInsightThresholds(
                companyId,
                lowPerformingBranchDeviationPct,
                criticalStockCoverageDays,
                lowStockMultiBranchCount,
                deadStockNoSaleDays,
                deadStockMinValue,
                marginDropPct,
                materialSalesDropPct,
                noSalesAlertDelayMinutes,
                minHistoryDaysForComparison,
                newBranchGraceDays,
                maxActiveInsightsPerCategory,
                insightCooldownHours,
                currencyCode,
                timezone,
                aiEnrichmentEnabled
        );
    }
}
