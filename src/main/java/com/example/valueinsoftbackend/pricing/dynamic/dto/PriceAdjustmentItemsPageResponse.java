package com.example.valueinsoftbackend.pricing.dynamic.dto;

import java.util.List;

public record PriceAdjustmentItemsPageResponse(
        int page,
        int size,
        long totalItems,
        int totalPages,
        boolean costDetailsIncluded,
        List<PriceAdjustmentItemPreviewResponse> items
) {
}
