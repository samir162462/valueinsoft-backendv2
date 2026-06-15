package com.example.valueinsoftbackend.loyalty.dto;

public record LoyaltyRecordedEarn(
        long loyaltyAccountId,
        int clientId,
        int pointsEarned,
        boolean inserted
) {
}
