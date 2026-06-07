package com.example.valueinsoftbackend.pricing.dynamic.dto;

public record PriceAdjustmentApplyResponse(
        long batchId,
        String status,
        int appliedItems,
        int skippedItems,
        int failedItems,
        String message
) {
}
