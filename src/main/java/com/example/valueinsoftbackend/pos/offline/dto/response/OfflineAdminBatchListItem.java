package com.example.valueinsoftbackend.pos.offline.dto.response;

import java.time.Instant;

public record OfflineAdminBatchListItem(
        Long companyId,
        Long branchId,
        Long batchId,
        String batchStatus,
        Instant createdAt,
        Instant receivedAt,
        Instant completedAt,
        int totalOrders,
        int syncedOrders,
        int failedOrders,
        int duplicateOrders,
        int needsReviewOrders,
        int validatedOrders,
        int postingFailedOrders,
        int validationFailedOrders,
        int warningCount
) {
}
