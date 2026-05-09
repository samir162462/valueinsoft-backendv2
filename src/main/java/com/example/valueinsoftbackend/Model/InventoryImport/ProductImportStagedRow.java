package com.example.valueinsoftbackend.Model.InventoryImport;

import java.util.Map;

public record ProductImportStagedRow(
        Long rowId,
        Integer rowNumber,
        String status,
        String action,
        Long existingProductId,
        Map<String, String> data
) {
}
