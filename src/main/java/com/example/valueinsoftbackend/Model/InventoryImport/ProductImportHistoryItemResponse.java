package com.example.valueinsoftbackend.Model.InventoryImport;

import java.time.OffsetDateTime;

public record ProductImportHistoryItemResponse(
        Long batchId,
        Integer companyId,
        Integer branchId,
        String mode,
        String status,
        String originalFileName,
        Long originalFileSize,
        boolean originalFileAvailable,
        boolean errorReportAvailable,
        Integer totalRows,
        Integer validRows,
        Integer warningRows,
        Integer invalidRows,
        Integer duplicateRows,
        Integer insertedRows,
        Integer updatedRows,
        Integer failedRows,
        String createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt,
        OffsetDateTime updatedAt
) {
}
