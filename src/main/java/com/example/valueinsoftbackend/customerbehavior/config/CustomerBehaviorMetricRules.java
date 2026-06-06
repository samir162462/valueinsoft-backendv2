package com.example.valueinsoftbackend.customerbehavior.config;

import java.util.List;

public final class CustomerBehaviorMetricRules {

    public static final String METRICS_VERSION = "customer-purchase-behavior-v1";
    public static final String PROMPT_VERSION = "customer-purchase-behavior-insights-v1";

    private CustomerBehaviorMetricRules() {
    }

    public static String grossOrderExpression(String alias) {
        return "COALESCE(" + alias + ".\"orderTotal\", 0)::numeric";
    }

    public static String discountExpression(String alias) {
        return "COALESCE(" + alias + ".\"orderDiscount\", 0)::numeric";
    }

    public static String returnValueExpression(String alias) {
        return "COALESCE(" + alias + ".\"orderBouncedBack\", 0)::numeric";
    }

    public static String netOrderExpression(String alias) {
        return "GREATEST(" + grossOrderExpression(alias)
                + " - " + discountExpression(alias)
                + " - " + returnValueExpression(alias)
                + ", 0)::numeric";
    }

    public static String validLinkedCustomerPredicate(String orderAlias, String customerAlias) {
        return orderAlias + ".\"clientId\" IS NOT NULL "
                + "AND " + orderAlias + ".\"clientId\" > 0 "
                + "AND " + customerAlias + ".c_id IS NOT NULL";
    }

    public static String activeLinePredicate(String detailAlias) {
        return "COALESCE(" + detailAlias + ".\"bouncedBack\", 0) = 0";
    }

    public static List<String> defaultDataQualityWarnings() {
        return List.of(
                "POS order tables do not expose status, cancelled, voided, or deleted columns; analytics include linked customer orders available in the POS tables.",
                "POS order tables do not expose tax or per-order currency columns; currency is resolved from Customer Behavior configuration or company settings.",
                "Payment method is read from orderType. The payType field belongs to inventory transactions, not POS orders.",
                "Returns use orderBouncedBack for value ratios. Detail bouncedBack is treated only as an item-level return indicator."
        );
    }
}
