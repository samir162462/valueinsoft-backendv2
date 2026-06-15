package com.example.valueinsoftbackend.loyalty.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record LoyaltyEstimateRequest(
        @Positive
        int branchId,

        @PositiveOrZero
        int clientId,

        @DecimalMin(value = "0.00")
        BigDecimal netAmount
) {
}
