package com.example.valueinsoftbackend.pricing.dynamic.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PricingMetricsSnapshot(
        long productId,
        String productName,
        String category,
        String businessLineKey,
        String templateKey,
        String pricingPolicyCode,
        Integer supplierId,
        BigDecimal retailPrice,
        BigDecimal lowestPrice,
        BigDecimal buyingPrice,
        BigDecimal stockQty,
        BigDecimal soldQty7d,
        BigDecimal soldQty30d,
        BigDecimal soldQty90d,
        BigDecimal salesVelocity7d,
        BigDecimal salesVelocity30d,
        BigDecimal salesVelocity90d,
        BigDecimal weightedVelocity,
        BigDecimal daysCover,
        BigDecimal currentMarginPct,
        ProductMovementClass movementClass,
        BigDecimal demandScore,
        BigDecimal costChangePct,
        OffsetDateTime lastSaleAt
) {
}
