package com.example.valueinsoftbackend.Model.InventoryImport;

import java.util.List;

public record ProductImportRowsPageResponse(
        List<ProductImportRowResponse> items,
        int page,
        int size,
        long totalItems
) {
}
