package com.example.valueinsoftbackend.pricing.dynamic.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record UsdPricingRateResponse(
        Long globalFxSnapshotId,
        BigDecimal globalRate,
        BigDecimal effectivePricingRate,
        BigDecimal safetyBufferPercentage,
        String selectedRateType,
        LocalDate effectiveDate,
        OffsetDateTime calculationTimestamp
) {
}
