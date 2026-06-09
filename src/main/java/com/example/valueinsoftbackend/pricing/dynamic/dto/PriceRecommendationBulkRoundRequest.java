package com.example.valueinsoftbackend.pricing.dynamic.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record PriceRecommendationBulkRoundRequest(
        @NotNull @Positive Integer companyId,
        @NotNull @Positive Integer branchId,
        @NotNull BigDecimal roundingFactor
) {
}
