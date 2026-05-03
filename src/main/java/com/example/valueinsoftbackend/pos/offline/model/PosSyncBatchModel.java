package com.example.valueinsoftbackend.pos.offline.model;

import com.example.valueinsoftbackend.pos.offline.enums.PosSyncBatchStatus;

import java.time.Instant;

/**
 * Row model for the pos_sync_batch table.
 */
public record PosSyncBatchModel(
        Long id,
        Long companyId,
        Long branchId,
        Long deviceId,
        Long cashierId,
        String clientBatchId,
        String clientType,
        String platform,
        String appVersion,
        PosSyncBatchStatus status,
        int totalOrders,
        int syncedOrders,
        int failedOrders,
        int duplicateOrders,
        int needsReviewOrders,
        Instant offlineStartedAt,
        Instant syncStartedAt,
        Instant syncCompletedAt,
        Instant createdAt,
        Instant updatedAt
) {}
