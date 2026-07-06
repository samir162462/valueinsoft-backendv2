package com.example.valueinsoftbackend.Model.Request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Pay an existing client for products the shop purchased from them.
 * Idempotent: retries with the same idempotencyKey replay the original result;
 * a different payload under the same key returns HTTP 409.
 */
@Data
public class ClientTradeInPaymentRequest {

    @Positive(message = "branchId is required")
    private int branchId;

    @Positive(message = "clientId is required")
    private int clientId;

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.0001", message = "amount must be greater than zero")
    @Digits(integer = 15, fraction = 4, message = "amount supports up to 4 decimal places")
    private BigDecimal amount;

    @NotBlank(message = "paymentMethod is required")
    @Size(max = 30)
    private String paymentMethod;

    @Size(max = 255)
    private String notes;

    @NotBlank(message = "idempotencyKey is required")
    @Size(max = 160)
    private String idempotencyKey;
}
