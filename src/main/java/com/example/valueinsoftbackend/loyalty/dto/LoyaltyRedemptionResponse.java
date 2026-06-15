package com.example.valueinsoftbackend.loyalty.dto;

import java.math.BigDecimal;

public record LoyaltyRedemptionResponse(
        long redemptionId,
        long rewardId,
        String rewardName,
        String status,
        int pointsRedeemed,
        BigDecimal discountAmount,
        String message
) {
}
