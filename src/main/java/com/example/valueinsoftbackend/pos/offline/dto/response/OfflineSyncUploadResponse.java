package com.example.valueinsoftbackend.pos.offline.dto.response;

import com.example.valueinsoftbackend.pos.offline.enums.PosSyncBatchStatus;

import java.util.List;

/**
 * Response returned after processing (or receiving) an offline sync upload.
 */
public record OfflineSyncUploadResponse(
        Long syncBatchId,
        String clientBatchId,
        PosSyncBatchStatus status,
        Integer totalOrders,
        Integer syncedOrders,
        Integer failedOrders,
        Integer duplicateOrders,
        Integer needsReviewOrders,
        List<OfflineOrderSyncResult> results
) {}
