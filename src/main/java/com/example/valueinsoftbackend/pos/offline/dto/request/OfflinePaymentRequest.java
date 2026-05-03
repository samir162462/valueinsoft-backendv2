package com.example.valueinsoftbackend.pos.offline.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A single payment tendered for an offline order.
 */
public record OfflinePaymentRequest(

        @NotNull(message = "paymentMethod is required")
        @Size(max = 50)
        String paymentMethod,

        @NotNull(message = "amount is required")
        @DecimalMin(value = "0", message = "amount must be >= 0")
        BigDecimal amount,

        @Size(max = 10)
        String currencyCode,

        @Size(max = 200)
        String localPaymentReference,

        Instant paidAt
) {}
