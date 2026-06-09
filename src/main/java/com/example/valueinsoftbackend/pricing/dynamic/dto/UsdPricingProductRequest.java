package com.example.valueinsoftbackend.pricing.dynamic.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record UsdPricingProductRequest(
        @NotNull @Positive Integer companyId,
        @NotNull @Positive Integer branchId,
        String query,
        String category,
        String businessLineKey,
        String templateKey,
        Integer supplierId,
        Boolean fxOnly,
        @DecimalMin("0.0000") @DecimalMax("0.9500") BigDecimal targetMarginPct,
        @Min(0) Integer page,
        @Min(1) @Max(100) Integer size
) {
}
