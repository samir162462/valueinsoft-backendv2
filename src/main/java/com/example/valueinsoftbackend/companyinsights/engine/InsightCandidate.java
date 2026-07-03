package com.example.valueinsoftbackend.companyinsights.engine;

import com.example.valueinsoftbackend.companyinsights.config.AllowedActionCode;
import com.example.valueinsoftbackend.companyinsights.config.InsightRole;
import com.example.valueinsoftbackend.companyinsights.config.InsightType;
import com.example.valueinsoftbackend.companyinsights.config.Severity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * An atomic, deterministic insight produced by a rule, pre-filled with backend-owned
 * slots and default (deterministic) rendering. The AI enrichment layer may later replace
 * the narrative but never the slots or metrics.
 */
public record InsightCandidate(
        long companyId,
        InsightType type,
        String insightKey,
        String periodType,          // DAY | WEEK | MONTH | ROLLING
        LocalDate periodStart,
        LocalDate periodEnd,
        Severity severity,
        int priorityScore,
        InsightRole role,
        String correlationGroup,    // nullable

        // deterministic default rendering (source of truth)
        String title,               // default-locale title (Arabic)
        String description,
        String executiveSummary,    // nullable
        Map<String, Object> localized,   // { "ar": {title,description,summary}, "en": {...} }
        Map<String, Object> slots,       // backend-owned slot values

        BigDecimal financialImpact, // nullable
        List<Long> affectedBranchIds,
        List<Long> affectedProductIds,
        List<Map<String, Object>> contributingFactors, // nullable

        AllowedActionCode actionCode,       // nullable
        Map<String, Object> actionContext,  // nullable
        Map<String, Object> sourceMetrics,
        String dataQualityStatus,
        String suppressedReason
) {
    public String severityName() {
        return severity.name();
    }

    public String categoryName() {
        return type.category().name();
    }

    /** Return a copy marked as suppressed by the correlation layer. */
    public InsightCandidate suppressed(String reason) {
        return new InsightCandidate(
                companyId, type, insightKey, periodType, periodStart, periodEnd, severity, priorityScore,
                InsightRole.SUPPRESSED, correlationGroup, title, description, executiveSummary, localized, slots,
                financialImpact, affectedBranchIds, affectedProductIds, contributingFactors, actionCode, actionContext,
                sourceMetrics, dataQualityStatus, reason);
    }
}
