package com.example.valueinsoftbackend.companyinsights.engine;

import com.example.valueinsoftbackend.companyinsights.config.CompanyInsightThresholds;

import java.time.LocalDate;
import java.util.List;

/**
 * A deterministic company-insight rule. Implementations read trusted snapshots and emit
 * zero or more atomic {@link InsightCandidate}s pre-filled with backend-owned slots.
 * A rule must never call the LLM and must fail-closed (emit nothing / a data-quality
 * candidate) when its data-quality preconditions are not met.
 */
public interface CompanyInsightRule {

    List<InsightCandidate> evaluate(RuleContext context);

    /**
     * Per-run execution context shared by all rules for one company.
     */
    record RuleContext(long companyId, CompanyInsightThresholds thresholds, LocalDate asOfDate) {
    }
}
