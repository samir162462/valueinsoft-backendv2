package com.example.valueinsoftbackend.Model.InventoryImport;

public record ProductImportColumn(
        String header,
        boolean required,
        String description
) {
}
