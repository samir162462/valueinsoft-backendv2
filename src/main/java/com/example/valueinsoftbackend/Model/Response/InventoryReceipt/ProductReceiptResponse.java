package com.example.valueinsoftbackend.Model.Response.InventoryReceipt;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductReceiptResponse {
    private String operationId;
    private boolean idempotentReplay;
    private ProductSummary product;
    private ReceiptSummary receipt;
    private LedgerSummary ledger;
    private FinanceSummary finance;
    private List<String> warnings = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductSummary {
        private long productId;
        private String productName;
        private String sku;
        private String barcode;
        private String trackingType;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReceiptSummary {
        private long receiptId;
        private int branchId;
        private int quantityReceived;
        private int previousQuantity;
        private int newQuantity;
        private int supplierId;
        private BigDecimal totalCost;
        private BigDecimal paidAmount;
        private BigDecimal remainingAmount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LedgerSummary {
        private long stockLedgerId;
        private String movementType;
        private int quantityDelta;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FinanceSummary {
        private String status;
        private String outboxEventId;
    }
}
