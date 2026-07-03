package com.example.valueinsoftbackend.companyinsights.config;

/**
 * Closed set of deep-link actions an insight may carry. The frontend maps each
 * code plus its action_context (structured params) to a concrete route + filters,
 * so no hard-coded URLs are persisted server-side.
 */
public enum AllowedActionCode {
    OPEN_PROFIT_REPORT,
    OPEN_BRANCH_PERFORMANCE_REPORT,
    OPEN_LOW_STOCK_INVENTORY,
    OPEN_DEAD_STOCK_INVENTORY,
    OPEN_BRANCH_POS
}
