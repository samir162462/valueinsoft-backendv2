package com.example.valueinsoftbackend.Model.InventoryImport;

import java.util.List;

public record ProductImportRowResponse(
        Long rowId,
        int rowNumber,
        String status,
        String action,
        String productName,
        String sku,
        String barcode,
        String category,
        String supplierName,
        Long existingProductId,
        List<ProductImportErrorResponse> errors
) {
}
