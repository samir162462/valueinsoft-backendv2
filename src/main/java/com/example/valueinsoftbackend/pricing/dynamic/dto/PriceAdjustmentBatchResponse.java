package com.example.valueinsoftbackend.pricing.dynamic.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PriceAdjustmentBatchResponse(
        long batchId,
        int companyId,
        int branchId,
        String sourceType,
        Long sourceRunId,
        String status,
        String adjustmentMode,
        String direction,
        BigDecimal adjustmentValue,
        String priceTargetsJson,
        int totalItems,
        int validItems,
        int warningItems,
        int blockedItems,
        int appliedItems,
        int failedItems,
        String reason,
        String createdBy,
        String submittedBy,
        String approvedBy,
        String rejectedBy,
        String appliedBy,
        OffsetDateTime createdAt,
        OffsetDateTime submittedAt,
        OffsetDateTime approvedAt,
        OffsetDateTime rejectedAt,
        OffsetDateTime appliedAt,
        OffsetDateTime updatedAt
) {
}
