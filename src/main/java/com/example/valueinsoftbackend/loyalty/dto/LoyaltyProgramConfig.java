package com.example.valueinsoftbackend.loyalty.dto;

import java.math.BigDecimal;

public record LoyaltyProgramConfig(
        long programId,
        Integer branchId,
        String status,
        String pointsName,
        BigDecimal earnAmount,
        int earnPoints,
        BigDecimal minEligibleAmount,
        Integer expiryMonths
) {
    public boolean active() {
        return "ACTIVE".equalsIgnoreCase(status);
    }
}
