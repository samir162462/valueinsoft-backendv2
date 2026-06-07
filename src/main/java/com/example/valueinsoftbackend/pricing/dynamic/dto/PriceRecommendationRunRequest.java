package com.example.valueinsoftbackend.pricing.dynamic.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.util.List;

public record PriceRecommendationRunRequest(
        @NotNull @Positive Integer companyId,
        @NotNull @Positive Integer branchId,
        @Min(1) @Max(180) Integer metricsWindowDays,
        LocalDate toDate,
        String query,
        List<Long> productIds,
        String category,
        String major,
        String businessLineKey,
        String templateKey,
        Integer supplierId,
        @Min(1) @Max(500) Integer maxProducts
) {
}
