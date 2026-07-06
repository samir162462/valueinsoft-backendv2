package com.example.valueinsoftbackend.Model.Billing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillingBalanceTopUpRequest {
    @NotNull
    @Positive
    private BigDecimal amount;

    @NotBlank
    private String idempotencyKey;

    private String note;
}
