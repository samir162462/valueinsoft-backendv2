package com.example.valueinsoftbackend.pricing.dynamic.dto;

import java.util.List;

public record PriceAdjustmentPreviewResponse(
        PriceAdjustmentBatchResponse batch,
        boolean costDetailsIncluded,
        List<PriceAdjustmentItemPreviewResponse> items
) {
}
