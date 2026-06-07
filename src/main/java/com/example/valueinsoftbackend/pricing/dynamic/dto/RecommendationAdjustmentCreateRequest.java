package com.example.valueinsoftbackend.pricing.dynamic.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record RecommendationAdjustmentCreateRequest(
        @NotNull @Positive Integer companyId,
        @NotNull @Positive Integer branchId,
        List<Long> recommendationItemIds,
        @Min(1) @Max(500) Integer maxProducts,
        @NotBlank String reason
) {
}
