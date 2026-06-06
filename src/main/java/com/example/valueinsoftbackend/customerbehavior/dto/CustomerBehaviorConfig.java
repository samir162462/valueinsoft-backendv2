package com.example.valueinsoftbackend.customerbehavior.dto;

import java.math.BigDecimal;

public record CustomerBehaviorConfig(
        Integer branchId,
        int newCustomerDays,
        int activeCustomerDays,
        int atRiskDays,
        int dormantDays,
        int loyalMinOrders,
        int vipMinOrders,
        BigDecimal vipMinSpend,
        BigDecimal discountSensitiveRatio,
        BigDecimal returnRiskRatio,
        int minimumAffinitySupport,
        String currencyCode,
        String timezone
) {
    public static CustomerBehaviorConfig defaults(String currencyCode, String timezone) {
        return new CustomerBehaviorConfig(
                null,
                30,
                60,
                90,
                180,
                3,
                5,
                BigDecimal.valueOf(50_000),
                BigDecimal.valueOf(0.15),
                BigDecimal.valueOf(0.10),
                3,
                currencyCode == null || currencyCode.isBlank() ? "EGP" : currencyCode,
                timezone == null || timezone.isBlank() ? "Africa/Cairo" : timezone
        );
    }
}
