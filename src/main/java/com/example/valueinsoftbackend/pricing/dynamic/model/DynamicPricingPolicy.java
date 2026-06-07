package com.example.valueinsoftbackend.pricing.dynamic.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record DynamicPricingPolicy(
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
        boolean active,
        String createdBy,
        String updatedBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
