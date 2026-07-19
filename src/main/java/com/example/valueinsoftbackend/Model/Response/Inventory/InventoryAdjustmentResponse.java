package com.example.valueinsoftbackend.Model.Response.Inventory;

public record InventoryAdjustmentResponse(
        String operationId,
        long productId,
        String productName,
        int quantityDelta,
        int previousQuantity,
        int newQuantity,
        int reservedQuantity,
        long balanceVersion,
        long ledgerId,
        long movementId,
        FinanceSummary finance,
        boolean idempotentReplay
) {
    public InventoryAdjustmentResponse asReplay() {
        return new InventoryAdjustmentResponse(
                operationId,
                productId,
                productName,
                quantityDelta,
                previousQuantity,
                newQuantity,
                reservedQuantity,
                balanceVersion,
                ledgerId,
                movementId,
                finance,
                true
        );
    }

    public record FinanceSummary(String status, String postingRequestId) {
    }
}
