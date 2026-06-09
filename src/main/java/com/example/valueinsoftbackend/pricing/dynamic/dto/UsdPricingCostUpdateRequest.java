package com.example.valueinsoftbackend.pricing.dynamic.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UsdPricingCostUpdateRequest(
        @NotNull @Positive Integer companyId,
        @NotNull @Positive Integer branchId,
        @NotNull Boolean fxPricingEnabled,
        @DecimalMin(value = "0.0001", message = "replacementCostUsd must be greater than zero")
        BigDecimal replacementCostUsd,
        @DecimalMin(value = "0.0001", message = "purchaseUsdRate must be greater than zero")
        BigDecimal purchaseUsdRate,
        @Size(max = 500) String note
) {
}
