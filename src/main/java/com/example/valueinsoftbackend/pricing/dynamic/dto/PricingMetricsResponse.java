package com.example.valueinsoftbackend.pricing.dynamic.dto;

import java.util.List;

public record PricingMetricsResponse(
        int page,
        int size,
        long totalItems,
        int totalPages,
        boolean costDetailsIncluded,
        List<PricingMetricsItemResponse> items
) {
}
