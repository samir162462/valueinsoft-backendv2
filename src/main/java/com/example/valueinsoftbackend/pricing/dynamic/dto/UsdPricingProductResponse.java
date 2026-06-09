package com.example.valueinsoftbackend.pricing.dynamic.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record UsdPricingProductResponse(
        long productId,
        String productName,
        String category,
        String businessLineKey,
        String templateKey,
        String pricingPolicyCode,
        Integer supplierId,
        String serial,
        String barcode,
        BigDecimal stockQty,
        boolean fxPricingEnabled,
        BigDecimal replacementCostUsd,
        String replacementCostCurrency,
        BigDecimal purchaseUsdRate,
        OffsetDateTime replacementCostUpdatedAt,
        Long globalFxSnapshotId,
        BigDecimal globalRate,
        BigDecimal effectivePricingRate,
        BigDecimal safetyBufferPercentage,
        String selectedRateType,
        java.time.LocalDate effectiveDate,
        BigDecimal purchaseCostLocal,
        BigDecimal replacementCostLocal,
        BigDecimal currentBuyingPrice,
        BigDecimal currentRetailPrice,
        BigDecimal currentLowestPrice,
        BigDecimal profitAmount,
        BigDecimal marginPct,
        BigDecimal markupPct,
        BigDecimal suggestedRetailPrice,
        BigDecimal suggestedLowestPrice,
        BigDecimal suggestedDeltaAmount,
        BigDecimal suggestedDeltaPct,
        String status
) {
}
