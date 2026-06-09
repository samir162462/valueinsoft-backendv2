package com.example.valueinsoftbackend.pricing.dynamic.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record UsdPricingProductsPageResponse(
        int page,
        int size,
        long totalItems,
        int totalPages,
        boolean costDetailsIncluded,
        Long globalFxSnapshotId,
        BigDecimal globalRate,
        BigDecimal effectivePricingRate,
        BigDecimal safetyBufferPercentage,
        String selectedRateType,
        java.time.LocalDate effectiveDate,
        OffsetDateTime calculationTimestamp,
        List<UsdPricingProductResponse> items
) {
}
