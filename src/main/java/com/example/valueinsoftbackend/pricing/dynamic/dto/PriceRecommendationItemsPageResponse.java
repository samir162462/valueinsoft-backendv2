package com.example.valueinsoftbackend.pricing.dynamic.dto;

import java.util.List;

public record PriceRecommendationItemsPageResponse(
        int page,
        int size,
        long totalItems,
        int totalPages,
        boolean costDetailsIncluded,
        List<PriceRecommendationItemResponse> items
) {
}
