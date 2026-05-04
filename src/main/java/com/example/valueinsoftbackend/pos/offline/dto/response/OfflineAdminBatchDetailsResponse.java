package com.example.valueinsoftbackend.pos.offline.dto.response;

import com.example.valueinsoftbackend.pos.offline.model.OfflineImportStatusCounts;

import java.time.Instant;
import java.util.List;

public record OfflineAdminBatchDetailsResponse(
        Long companyId,
        Long branchId,
        Long batchId,
        String batchStatus,
        Instant createdAt,
        Instant receivedAt,
        Instant completedAt,
        OfflineImportStatusCounts importStatusCounts,
        int totalOrders,
        int syncedOrders,
        int failedOrders,
        int duplicateOrders,
        int needsReviewOrders,
        int eligibleForPostingCount,
        List<String> warnings,
        OfflineAdminReadiness readiness,
        List<OfflineAdminRecentEvent> recentAdminEvents,
        OfflineAdminErrorSummary errorSummary
) {
}
