package com.example.valueinsoftbackend.Model.InventoryImport;

import java.time.OffsetDateTime;

public record ProductImportBatchSummaryResponse(
        Long batchId,
        Integer companyId,
        Integer branchId,
        String importType,
        String mode,
        String status,
        Integer totalRows,
        Integer validRows,
        Integer warningRows,
        Integer invalidRows,
        Integer duplicateRows,
        Integer insertedRows,
        Integer updatedRows,
        Integer skippedRows,
        Integer failedRows,
        String originalFileName,
        String createdBy,
        String confirmedBy,
        OffsetDateTime createdAt,
        OffsetDateTime validatedAt,
        OffsetDateTime confirmedAt,
        OffsetDateTime completedAt,
        OffsetDateTime updatedAt,
        boolean canConfirm
) {
}
