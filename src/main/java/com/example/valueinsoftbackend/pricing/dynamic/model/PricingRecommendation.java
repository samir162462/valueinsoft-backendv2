package com.example.valueinsoftbackend.pricing.dynamic.model;

import java.math.BigDecimal;
import java.util.List;

public record PricingRecommendation(
        PricingMetricsSnapshot metrics,
        DynamicPricingPolicy policy,
        BigDecimal suggestedRetailPrice,
        BigDecimal suggestedLowestPrice,
        BigDecimal deltaAmount,
        BigDecimal deltaPct,
        BigDecimal suggestedMarginPct,
        PriceRecommendationStatus status,
        boolean approvalRequired,
        List<PriceReasonCode> reasonCodes,
        List<PriceReasonCode> warningCodes,
        String explanationJson
) {
}
