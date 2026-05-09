package com.example.valueinsoftbackend.Model.InventoryImport;

import java.util.List;

public record ProductImportConfirmResponse(
        Long batchId,
        String status,
        int eligibleRows,
        int insertedRows,
        int updatedRows,
        int skippedRows,
        int failedRows,
        boolean completed,
        List<String> messages
) {
}
