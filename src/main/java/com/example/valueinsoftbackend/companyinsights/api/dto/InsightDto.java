package com.example.valueinsoftbackend.companyinsights.api.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Insight representation returned to the Admin UI. Used for both list items and detail
 * (heavy JSON fields are populated for detail reads).
 */
public record InsightDto(
        long id,
        String insightType,
        String severity,
        String category,
        String status,
        String role,
        int priorityScore,
        String periodType,
        String periodStart,
        String periodEnd,
        String title,
        String description,
        String executiveSummary,
        BigDecimal financialImpact,
        List<Long> affectedBranchIds,
        List<Long> affectedProductIds,
        int occurrenceCount,
        String lastDetectedAt,
        String actionCode,
        Object actionContext,
        String dataQualityStatus,
        String enrichmentSource,
        Object localized,
        Object slots,
        Object contributingFactors,
        Object sourceMetrics
) {
}
