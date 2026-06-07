package com.example.valueinsoftbackend.pricing.dynamic.dto;

import com.example.valueinsoftbackend.pricing.dynamic.model.PricingMetricsSnapshot;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PricingMetricsItemResponse(
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
        String movementClass,
        BigDecimal demandScore,
        BigDecimal costChangePct,
        OffsetDateTime lastSaleAt
) {
    public static PricingMetricsItemResponse from(PricingMetricsSnapshot snapshot, boolean includeCostDetails) {
        return new PricingMetricsItemResponse(
                snapshot.productId(),
                snapshot.productName(),
                snapshot.category(),
                snapshot.businessLineKey(),
                snapshot.templateKey(),
                snapshot.pricingPolicyCode(),
                snapshot.supplierId(),
                snapshot.retailPrice(),
                snapshot.lowestPrice(),
                includeCostDetails ? snapshot.buyingPrice() : null,
                snapshot.stockQty(),
                snapshot.soldQty7d(),
                snapshot.soldQty30d(),
                snapshot.soldQty90d(),
                snapshot.salesVelocity7d(),
                snapshot.salesVelocity30d(),
                snapshot.salesVelocity90d(),
                snapshot.weightedVelocity(),
                snapshot.daysCover(),
                includeCostDetails ? snapshot.currentMarginPct() : null,
                snapshot.movementClass().name(),
                snapshot.demandScore(),
                includeCostDetails ? snapshot.costChangePct() : null,
                snapshot.lastSaleAt()
        );
    }
}
