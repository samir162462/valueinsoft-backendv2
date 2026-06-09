package com.example.valueinsoftbackend.pricing.dynamic.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;

public record InflationApplyRequest(
        @NotNull @Positive Integer companyId,
        @NotNull @Positive Integer branchId,
        String scopeType,
        String scopeValue,
        @NotNull BigDecimal rate,
        boolean adjustBuying,
        boolean adjustRetail,
        boolean adjustLowest,
        @NotNull String mode,
        String direction,
        BigDecimal roundingFactor,
        List<Long> productIds
) {}
