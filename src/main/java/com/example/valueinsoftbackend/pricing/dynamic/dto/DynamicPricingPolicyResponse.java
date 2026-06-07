package com.example.valueinsoftbackend.pricing.dynamic.dto;

import com.example.valueinsoftbackend.pricing.dynamic.model.DynamicPricingPolicy;

import java.math.BigDecimal;

public record DynamicPricingPolicyResponse(
        Long policyId,
        Long companyId,
        Long branchId,
        String scopeType,
        String scopeValue,
        String displayName,
        BigDecimal targetMarginPct,
        BigDecimal minMarginPct,
        BigDecimal maxIncreasePct,
        BigDecimal maxDecreasePct,
        BigDecimal maxIncreaseAmount,
        BigDecimal maxDecreaseAmount,
        BigDecimal minFinalPrice,
        BigDecimal maxFinalPrice,
        boolean allowBelowCost,
        boolean approvalRequired,
        boolean makerCheckerRequired,
        boolean autoApplyAllowed,
        BigDecimal maxAutoApplyPct,
        int maxProductsPerBatch,
        String roundingMode,
        BigDecimal lowStockDaysCover,
        BigDecimal overstockDaysCover,
        int slowMovingDays,
        int deadStockDays,
        String configJson,
        boolean active
) {
    public static DynamicPricingPolicyResponse from(DynamicPricingPolicy policy) {
        return new DynamicPricingPolicyResponse(
                policy.policyId(),
                policy.companyId(),
                policy.branchId(),
                policy.scopeType(),
                policy.scopeValue(),
                policy.displayName(),
                policy.targetMarginPct(),
                policy.minMarginPct(),
                policy.maxIncreasePct(),
                policy.maxDecreasePct(),
                policy.maxIncreaseAmount(),
                policy.maxDecreaseAmount(),
                policy.minFinalPrice(),
                policy.maxFinalPrice(),
                policy.allowBelowCost(),
                policy.approvalRequired(),
                policy.makerCheckerRequired(),
                policy.autoApplyAllowed(),
                policy.maxAutoApplyPct(),
                policy.maxProductsPerBatch(),
                policy.roundingMode(),
                policy.lowStockDaysCover(),
                policy.overstockDaysCover(),
                policy.slowMovingDays(),
                policy.deadStockDays(),
                policy.configJson(),
                policy.active()
        );
    }
}
