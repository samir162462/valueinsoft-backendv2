package com.example.valueinsoftbackend.pricing.dynamic.dto;

import java.time.OffsetDateTime;

public record PriceRecommendationRunResponse(
        long runId,
        int companyId,
        int branchId,
        String status,
        int metricsWindowDays,
        int totalProducts,
        int recommendedProducts,
        int warningProducts,
        int skippedProducts,
        String createdBy,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        String failureReason
) {
}
