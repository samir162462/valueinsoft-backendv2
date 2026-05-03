package com.example.valueinsoftbackend.pos.offline.dto.response;

import com.example.valueinsoftbackend.pos.offline.enums.OfflineErrorSeverity;

import java.time.Instant;

public record SyncErrorItemResponse(
        Long errorId,
        Long offlineOrderImportId,
        String offlineOrderNo,
        String errorStage,
        String errorCode,
        String errorMessage,
        String fieldPath,
        OfflineErrorSeverity severity,
        Boolean retryAllowed,
        Boolean managerReviewRequired,
        Instant createdAt
) {
}
