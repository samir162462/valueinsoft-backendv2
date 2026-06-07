package com.example.valueinsoftbackend.pricing.dynamic.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record PriceRecommendationItemResponse(
        long itemId,
        long runId,
        long productId,
        String productName,
        String category,
        String pricingPolicyCode,
        BigDecimal oldRetailPrice,
        BigDecimal oldLowestPrice,
        BigDecimal buyingPrice,
        BigDecimal suggestedRetailPrice,
        BigDecimal suggestedLowestPrice,
        BigDecimal deltaAmount,
        BigDecimal deltaPct,
        BigDecimal currentMarginPct,
        BigDecimal suggestedMarginPct,
        BigDecimal stockQty,
        BigDecimal salesVelocity7d,
        BigDecimal salesVelocity30d,
        BigDecimal salesVelocity90d,
        BigDecimal daysCover,
        String movementClass,
        BigDecimal demandScore,
        BigDecimal costChangePct,
        String recommendationStatus,
        boolean approvalRequired,
        List<String> reasonCodes,
        String explanationJson,
        List<String> warningCodes,
        OffsetDateTime createdAt
) {
}
