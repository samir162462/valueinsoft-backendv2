package com.example.valueinsoftbackend.Model.Response.Inventory;

public record InventoryDamageResponse(
        String operationId,
        long damageId,
        long productId,
        String productName,
        int quantityDelta,
        int previousQuantity,
        int newQuantity,
        int reservedQuantity,
        long balanceVersion,
        String status,
        long ledgerId,
        long movementId,
        FinanceSummary finance,
        boolean idempotentReplay
) {
    public InventoryDamageResponse asReplay() {
        return new InventoryDamageResponse(
                operationId, damageId, productId, productName, quantityDelta, previousQuantity,
                newQuantity, reservedQuantity, balanceVersion, status, ledgerId, movementId, finance, true);
    }

    public record FinanceSummary(String status, String postingRequestId) {
    }
}
