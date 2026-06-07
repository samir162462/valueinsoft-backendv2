package com.example.valueinsoftbackend.pricing.dynamic.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record DynamicPricingPolicyRequest(
        Long policyId,
        @NotNull @Positive Long companyId,
        @Positive Long branchId,
        @NotBlank @Pattern(regexp = "COMPANY|BRANCH|CATEGORY|PRODUCT|BUSINESS_LINE|PRICING_POLICY") String scopeType,
        String scopeValue,
        @NotBlank String displayName,
        @NotNull @DecimalMin("0.0000") @DecimalMax("0.9999") BigDecimal targetMarginPct,
        @NotNull @DecimalMin("0.0000") @DecimalMax("0.9999") BigDecimal minMarginPct,
        @NotNull @DecimalMin("0.0000") BigDecimal maxIncreasePct,
        @NotNull @DecimalMin("0.0000") BigDecimal maxDecreasePct,
        @PositiveOrZero BigDecimal maxIncreaseAmount,
        @PositiveOrZero BigDecimal maxDecreaseAmount,
        @PositiveOrZero BigDecimal minFinalPrice,
        @PositiveOrZero BigDecimal maxFinalPrice,
        Boolean allowBelowCost,
        Boolean approvalRequired,
        Boolean makerCheckerRequired,
        Boolean autoApplyAllowed,
        @PositiveOrZero BigDecimal maxAutoApplyPct,
        @Min(1) Integer maxProductsPerBatch,
        @Pattern(regexp = "NEAREST_1|NEAREST_5|NEAREST_10|CEILING_1") String roundingMode,
        @PositiveOrZero BigDecimal lowStockDaysCover,
        @PositiveOrZero BigDecimal overstockDaysCover,
        @Min(1) Integer slowMovingDays,
        @Min(1) Integer deadStockDays,
        String configJson,
        Boolean active
) {
}
