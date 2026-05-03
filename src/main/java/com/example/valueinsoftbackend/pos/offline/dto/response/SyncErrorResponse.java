package com.example.valueinsoftbackend.pos.offline.dto.response;

import com.example.valueinsoftbackend.pos.offline.enums.OfflineErrorSeverity;

import java.time.Instant;

/**
 * Response for a single sync processing error.
 */
public record SyncErrorResponse(
        Long errorId,
        Long offlineOrderImportId,
        String errorStage,
        String errorCode,
        String errorMessage,
        String fieldPath,
        String rawValue,
        OfflineErrorSeverity severity,
        Boolean retryAllowed,
        Boolean managerReviewRequired,
        Instant createdAt
) {}
