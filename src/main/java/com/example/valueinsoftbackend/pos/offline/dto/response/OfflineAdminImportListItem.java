package com.example.valueinsoftbackend.pos.offline.dto.response;

import java.time.Instant;
import java.util.UUID;

public record OfflineAdminImportListItem(
        Long offlineOrderImportId,
        String offlineOrderNo,
        String localOrderId,
        String status,
        Instant createdAt,
        Instant updatedAt,
        Long postedOrderId,
        Long officialOrderId,
        UUID financePostingRequestId,
        String financeEnqueueStatus,
        String errorCode,
        String errorMessage,
        int retryCount,
        String deviceCode,
        Long cashierId
) {
}

