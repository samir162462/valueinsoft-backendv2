package com.example.valueinsoftbackend.pos.offline.dto.response;

import com.example.valueinsoftbackend.pos.offline.enums.OfflineOrderImportStatus;

import java.time.Instant;

public record OfflineRetryResultResponse(
        Long offlineOrderImportId,
        String offlineOrderNo,
        String idempotencyKey,
        OfflineOrderImportStatus previousStatus,
        OfflineOrderImportStatus newStatus,
        Integer retryCount,
        Instant lastRetryAt,
        Boolean accepted,
        String message
) {
}
