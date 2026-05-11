package com.example.valueinsoftbackend.ai.tools;

public record InventoryAiProductDto(
        Long productId,
        String productName,
        String barcode,
        String category,
        String productType,
        Integer quantityOnHand,
        Integer reservedQuantity,
        Integer availableQuantity,
        String stockStatus,
        Integer retailPrice
) {
}
