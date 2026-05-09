package com.example.valueinsoftbackend.Model.InventoryImport;

public record ProductImportErrorResponse(
        String fieldName,
        String code,
        String message,
        String severity,
        String rawValue
) {
}
