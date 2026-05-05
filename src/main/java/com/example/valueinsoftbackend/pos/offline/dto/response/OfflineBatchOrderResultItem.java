package com.example.valueinsoftbackend.pos.offline.dto.response;

import com.example.valueinsoftbackend.pos.offline.enums.OfflineOrderImportStatus;
import java.time.Instant;

public record OfflineBatchOrderResultItem(
        Long offlineOrderImportId,
        String offlineOrderNo,
        String localOrderId,
        String idempotencyKey,
        OfflineOrderImportStatus status,
        Instant createdAt,
        Instant updatedAt,
        Long postedOrderId,
        Long officialOrderId,
        String errorCode,
        String errorMessage,
        String postingErrorCode,
        String postingErrorMessage,
        String financeEnqueueStatus,
        Long financePostingRequestId,
        String payloadHashPrefix
) {}
