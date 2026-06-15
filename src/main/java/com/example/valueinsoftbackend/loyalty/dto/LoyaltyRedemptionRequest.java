package com.example.valueinsoftbackend.loyalty.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record LoyaltyRedemptionRequest(
        @Positive
        int branchId,

        @Positive
        int clientId,

        Long rewardId,

        @DecimalMin(value = "0.00")
        BigDecimal orderNetAmount
) {
}
