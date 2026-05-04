package com.example.valueinsoftbackend.pos.offline.model;

public record OfflineImportStatusCounts(
        int totalCount,
        int pendingCount,
        int pendingRetryCount,
        int processingCount,
        int readyForValidationCount,
        int validatingCount,
        int validatedCount,
        int postingCount,
        int syncedCount,
        int postingFailedCount,
        int validationFailedCount,
        int failedCount,
        int duplicateCount,
        int needsReviewCount
) {
    public int activeCount() {
        return pendingCount
                + pendingRetryCount
                + processingCount
                + readyForValidationCount
                + validatingCount
                + validatedCount
                + postingCount;
    }

    public int issueCount() {
        return postingFailedCount
                + validationFailedCount
                + failedCount
                + duplicateCount
                + needsReviewCount;
    }
}
