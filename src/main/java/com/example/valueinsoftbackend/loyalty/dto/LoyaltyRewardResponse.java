package com.example.valueinsoftbackend.loyalty.dto;

import java.math.BigDecimal;

public record LoyaltyRewardResponse(
        long rewardId,
        String rewardName,
        String rewardType,
        int pointsCost,
        BigDecimal discountAmount,
        BigDecimal minimumSpend,
        boolean eligible,
        String reason
) {
}
