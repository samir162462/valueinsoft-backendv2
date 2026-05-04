package com.example.valueinsoftbackend.pos.offline.dto.response;

public record OfflineAdminOperationResponse(
        Long companyId,
        Long branchId,
        Long batchId,
        String operation,
        boolean accepted,
        String message,
        int processedCount,
        int failedCount,
        boolean summaryRecalculated
) {
}
