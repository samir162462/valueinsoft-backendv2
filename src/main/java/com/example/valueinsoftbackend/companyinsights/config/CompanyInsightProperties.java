package com.example.valueinsoftbackend.companyinsights.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Feature/runtime configuration for Company Smart Insights.
 * Bound to prefix {@code vls.company-insights}. Business thresholds live per-company
 * in {@code public.company_insight_settings}; this holds engine/runtime knobs.
 */
@ConfigurationProperties(prefix = "vls.company-insights")
public class CompanyInsightProperties {

    /** Master switch for the whole feature (jobs + APIs). */
    private boolean enabled = true;

    /** Number of trailing days re-closed on each daily aggregation run (late orders/returns). */
    private int trailingRecloseDays = 3;

    /** Default dead-stock no-sale window used when computing the informational snapshot value. */
    private int snapshotDeadStockDays = 60;

    /** Fallback low-stock threshold if a branch has no configured value. */
    private int defaultLowStockThreshold = 5;

    /** Debounce window (minutes) for dirty-queue driven inventory recompute. */
    private int dirtyDebounceMinutes = 10;

    /** Backfill chunk size in days. */
    private int backfillChunkDays = 7;

    /** Max insights enriched by AI per company per run (cost guard). */
    private int aiMaxInsightsPerRun = 20;

    /** Insight expiry in days when a rule does not set an explicit expiry. */
    private int defaultInsightTtlDays = 30;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getTrailingRecloseDays() {
        return trailingRecloseDays;
    }

    public void setTrailingRecloseDays(int trailingRecloseDays) {
        this.trailingRecloseDays = trailingRecloseDays;
    }

    public int getSnapshotDeadStockDays() {
        return snapshotDeadStockDays;
    }

    public void setSnapshotDeadStockDays(int snapshotDeadStockDays) {
        this.snapshotDeadStockDays = snapshotDeadStockDays;
    }

    public int getDefaultLowStockThreshold() {
        return defaultLowStockThreshold;
    }

    public void setDefaultLowStockThreshold(int defaultLowStockThreshold) {
        this.defaultLowStockThreshold = defaultLowStockThreshold;
    }

    public int getDirtyDebounceMinutes() {
        return dirtyDebounceMinutes;
    }

    public void setDirtyDebounceMinutes(int dirtyDebounceMinutes) {
        this.dirtyDebounceMinutes = dirtyDebounceMinutes;
    }

    public int getBackfillChunkDays() {
        return backfillChunkDays;
    }

    public void setBackfillChunkDays(int backfillChunkDays) {
        this.backfillChunkDays = backfillChunkDays;
    }

    public int getAiMaxInsightsPerRun() {
        return aiMaxInsightsPerRun;
    }

    public void setAiMaxInsightsPerRun(int aiMaxInsightsPerRun) {
        this.aiMaxInsightsPerRun = aiMaxInsightsPerRun;
    }

    public int getDefaultInsightTtlDays() {
        return defaultInsightTtlDays;
    }

    public void setDefaultInsightTtlDays(int defaultInsightTtlDays) {
        this.defaultInsightTtlDays = defaultInsightTtlDays;
    }
}
