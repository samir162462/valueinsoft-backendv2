package com.example.valueinsoftbackend.pricing.dynamic.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.util.List;

public record PricingMetricsRequest(
        @NotNull @Positive Integer companyId,
        @NotNull @Positive Integer branchId,
        @NotNull LocalDate fromDate,
        @NotNull LocalDate toDate,
        String query,
        List<Long> productIds,
        String category,
        String major,
        String businessLineKey,
        String templateKey,
        Integer supplierId,
        @Min(0) Integer page,
        @Min(1) @Max(100) Integer size
) {
}
