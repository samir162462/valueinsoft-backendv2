package com.example.valueinsoftbackend.loyalty.dto;

import java.math.BigDecimal;

public record LoyaltyEstimateResponse(
        boolean eligible,
        int projectedEarnPoints,
        int currentAvailablePoints,
        String pointsName,
        int earnPoints,
        BigDecimal earnAmount,
        String message
) {
}
