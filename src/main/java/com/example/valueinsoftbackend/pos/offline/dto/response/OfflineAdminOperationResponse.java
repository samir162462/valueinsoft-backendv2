package com.example.valueinsoftbackend.pos.offline.dto.response;

import java.util.List;

public record OfflineAdminOperationResponse(
        Long companyId,
        Long branchId,
        Long batchId,
        String operation,
        boolean accepted,
        String batchStatus,
        String message,
        int processedCount,
        int postedCount,
        int skippedCount,
        int failedCount,
        int validationFailedCount,
        int postingFailedCount,
        int needsReviewCount,
        int eligibleForPostingCount,
        boolean summaryRecalculated,
        List<String> warnings
) {
}
