package com.example.valueinsoftbackend.companyinsights.config;

/**
 * MVP company-level insight types. PROFIT_MARGIN_DROP is intentionally not a
 * separate type in the MVP; it is a contributing condition within
 * {@link #COMPANY_WEEKLY_PERFORMANCE}.
 */
public enum InsightType {
    COMPANY_WEEKLY_PERFORMANCE(InsightCategory.PERFORMANCE),
    LOW_PERFORMING_BRANCH(InsightCategory.PERFORMANCE),
    COMPANY_WIDE_LOW_STOCK(InsightCategory.INVENTORY),
    DEAD_STOCK_COMPANY_WIDE(InsightCategory.INVENTORY),
    BRANCH_NO_ACTIVITY(InsightCategory.ACTIVITY);

    private final InsightCategory category;

    InsightType(InsightCategory category) {
        this.category = category;
    }

    public InsightCategory category() {
        return category;
    }
}
