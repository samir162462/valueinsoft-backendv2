package com.example.valueinsoftbackend.Model.InventoryImport;

import java.util.List;

public record ProductImportHistoryResponse(
        List<ProductImportHistoryItemResponse> items,
        int page,
        int size,
        long totalItems
) {
}
