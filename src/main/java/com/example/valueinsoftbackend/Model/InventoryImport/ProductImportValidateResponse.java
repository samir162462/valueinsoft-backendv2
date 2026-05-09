package com.example.valueinsoftbackend.Model.InventoryImport;

import java.util.List;

public record ProductImportValidateResponse(
        Long batchId,
        String status,
        String mode,
        int totalRows,
        int validRows,
        int warningRows,
        int invalidRows,
        int duplicateRows,
        boolean canConfirm,
        List<String> warnings
) {
}
