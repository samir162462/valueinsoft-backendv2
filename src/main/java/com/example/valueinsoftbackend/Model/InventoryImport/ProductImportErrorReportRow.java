package com.example.valueinsoftbackend.Model.InventoryImport;

import java.util.List;
import java.util.Map;

public record ProductImportErrorReportRow(
        Integer rowNumber,
        String status,
        Map<String, String> rawData,
        List<ProductImportErrorResponse> errors
) {
}
