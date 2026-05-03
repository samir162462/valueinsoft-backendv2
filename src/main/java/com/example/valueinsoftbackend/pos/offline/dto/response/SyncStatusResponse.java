package com.example.valueinsoftbackend.pos.offline.dto.response;

import com.example.valueinsoftbackend.pos.offline.enums.PosSyncBatchStatus;

import java.time.Instant;

/**
 * Response for querying the current status of a sync batch.
 */
public record SyncStatusResponse(
        Long syncBatchId,
        String clientBatchId,
        PosSyncBatchStatus status,
        Integer totalOrders,
        Integer syncedOrders,
        Integer failedOrders,
        Integer duplicateOrders,
        Integer needsReviewOrders,
        Instant createdAt,
        Instant completedAt
) {}
