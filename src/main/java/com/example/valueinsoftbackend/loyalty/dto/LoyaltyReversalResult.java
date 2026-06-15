package com.example.valueinsoftbackend.loyalty.dto;

public record LoyaltyReversalResult(
        int earnedPointsReversed,
        int redeemedPointsRestored,
        boolean inserted
) {
}
