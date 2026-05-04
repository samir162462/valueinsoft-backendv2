package com.example.valueinsoftbackend.pos.offline.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OfflineAdminImportDetailsResponse(
        Long companyId,
        Long branchId,
        Long batchId,
        Long offlineOrderImportId,
        String offlineOrderNo,
        String status,
        Instant createdAt,
        Instant updatedAt,
        Instant processingStartedAt,
        Instant postingStartedAt,
        Instant postingCompletedAt,
        Long postedOrderId,
        Long officialOrderId,
        UUID financePostingRequestId,
        UUID financeJournalEntryId,
        String financeEnqueueStatus,
        String financeEnqueueError,
        String errorCode,
        String errorMessage,
        Integer retryCount,
        Instant lastRetryAt,
        Long deviceId,
        String deviceCode,
        Long cashierId,
        String idempotencyKey,
        String payloadHashPrefix,
        String idempotencyStatus,
        String idempotencyResultMetadata,
        OfflineAdminOnlineOrderReference onlineOrderReference,
        List<OfflineAdminImportErrorItem> errors,
        List<OfflineAdminImportAuditEvent> auditEvents
) {
}
