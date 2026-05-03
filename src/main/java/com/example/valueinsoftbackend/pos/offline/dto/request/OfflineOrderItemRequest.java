package com.example.valueinsoftbackend.pos.offline.dto.request;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/**
 * A single line item within an offline order.
 */
public record OfflineOrderItemRequest(

        @Positive(message = "productId must be positive")
        Long productId,

        @Size(max = 100)
        String barcode,

        @Size(max = 300)
        String productSnapshotName,

        @NotNull(message = "quantity is required")
        @DecimalMin(value = "0.0001", message = "quantity must be > 0")
        BigDecimal quantity,

        @NotNull(message = "unitPrice is required")
        @DecimalMin(value = "0", message = "unitPrice must be >= 0")
        BigDecimal unitPrice,

        @DecimalMin(value = "0", message = "discountAmount must be >= 0")
        BigDecimal discountAmount,

        @DecimalMin(value = "0", message = "taxRate must be >= 0")
        BigDecimal taxRate,

        @DecimalMin(value = "0", message = "taxAmount must be >= 0")
        BigDecimal taxAmount,

        @NotNull(message = "lineTotal is required")
        @DecimalMin(value = "0", message = "lineTotal must be >= 0")
        BigDecimal lineTotal
) {}
