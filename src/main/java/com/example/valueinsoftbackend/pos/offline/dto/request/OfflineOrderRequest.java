package com.example.valueinsoftbackend.pos.offline.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * A single offline order within a sync upload batch.
 */
public record OfflineOrderRequest(

        @NotBlank(message = "offlineOrderNo is required")
        @Size(max = 150)
        String offlineOrderNo,

        @NotBlank(message = "idempotencyKey is required")
        @Size(max = 200)
        String idempotencyKey,

        Instant localOrderCreatedAt,

        Long customerId,

        @Valid
        LocalCustomerRequest localCustomer,

        @Size(max = 50)
        String saleType,

        @Size(max = 10)
        String currencyCode,

        @NotNull(message = "subtotalAmount is required")
        @DecimalMin(value = "0", message = "subtotalAmount must be >= 0")
        BigDecimal subtotalAmount,

        @DecimalMin(value = "0", message = "discountAmount must be >= 0")
        BigDecimal discountAmount,

        @DecimalMin(value = "0", message = "taxAmount must be >= 0")
        BigDecimal taxAmount,

        @NotNull(message = "totalAmount is required")
        @DecimalMin(value = "0", message = "totalAmount must be >= 0")
        BigDecimal totalAmount,

        @Size(max = 30)
        String paymentStatus,

        @Size(max = 100)
        String localShiftId,

        @Size(max = 500)
        String notes,

        @NotEmpty(message = "items cannot be empty")
        @Valid
        List<OfflineOrderItemRequest> items,

        @Valid
        List<OfflinePaymentRequest> payments
) {}
