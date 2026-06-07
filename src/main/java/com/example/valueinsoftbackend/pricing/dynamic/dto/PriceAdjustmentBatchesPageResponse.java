package com.example.valueinsoftbackend.pricing.dynamic.dto;

import java.util.List;

public record PriceAdjustmentBatchesPageResponse(
        int page,
        int size,
        long totalItems,
        int totalPages,
        List<PriceAdjustmentBatchResponse> items
) {
}
