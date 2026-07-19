package com.example.valueinsoftbackend.Model.Response.Inventory;

public record InventorySupplierReturnResponse(
        String operationId,
        long supplierReturnId,
        long productId,
        String productName,
        int supplierId,
        int quantityReturned,
        int previousQuantity,
        int newQuantity,
        int reservedQuantity,
        long balanceVersion,
        long ledgerId,
        long movementId,
        FinanceSummary finance,
        boolean idempotentReplay
) {
    public InventorySupplierReturnResponse asReplay() {
        return new InventorySupplierReturnResponse(
                operationId, supplierReturnId, productId, productName, supplierId, quantityReturned,
                previousQuantity, newQuantity, reservedQuantity, balanceVersion, ledgerId, movementId,
                finance, true
        );
    }

    public record FinanceSummary(String status, String postingRequestId) {
    }
}
