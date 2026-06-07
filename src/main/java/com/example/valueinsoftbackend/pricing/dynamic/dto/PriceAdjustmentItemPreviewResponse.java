package com.example.valueinsoftbackend.pricing.dynamic.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record PriceAdjustmentItemPreviewResponse(
        long itemId,
        long batchId,
        long productId,
        Long recommendationItemId,
        String productName,
        BigDecimal oldRetailPrice,
        BigDecimal newRetailPrice,
        BigDecimal oldLowestPrice,
        BigDecimal newLowestPrice,
        BigDecimal buyingPriceSnapshot,
        BigDecimal deltaAmount,
        BigDecimal deltaPct,
        BigDecimal expectedMarginPct,
        String status,
        List<String> reasonCodes,
        List<String> warningCodes,
        List<String> blockedCodes,
        String applyError,
        OffsetDateTime createdAt,
        OffsetDateTime appliedAt
) {
}
