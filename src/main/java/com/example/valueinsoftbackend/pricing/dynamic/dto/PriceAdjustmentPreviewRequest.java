package com.example.valueinsoftbackend.pricing.dynamic.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

public record PriceAdjustmentPreviewRequest(
        @NotNull @Positive Integer companyId,
        @NotNull @Positive Integer branchId,
        @NotBlank String adjustmentMode,
        @NotBlank String direction,
        @NotNull @DecimalMin(value = "0.0001") BigDecimal adjustmentValue,
        String priceTarget,
        String query,
        List<Long> productIds,
        String category,
        String major,
        String businessLineKey,
        String templateKey,
        Integer supplierId,
        @Min(1) @Max(500) Integer maxProducts,
        @NotBlank String reason
) {
}
