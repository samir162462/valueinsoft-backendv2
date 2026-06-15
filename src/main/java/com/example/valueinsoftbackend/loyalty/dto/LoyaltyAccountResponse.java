package com.example.valueinsoftbackend.loyalty.dto;

import java.time.LocalDateTime;

public record LoyaltyAccountResponse(
        long loyaltyAccountId,
        int clientId,
        String clientName,
        String phone,
        String status,
        int availablePoints,
        int pendingPoints,
        int lifetimePoints,
        int redeemedPoints,
        int expiredPoints,
        String tierName,
        LocalDateTime lastActivityAt,
        String pointsName,
        int earnPoints,
        java.math.BigDecimal earnAmount
) {
}
