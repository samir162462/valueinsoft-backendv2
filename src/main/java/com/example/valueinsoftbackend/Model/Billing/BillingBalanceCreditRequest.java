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
public class BillingBalanceCreditRequest {
    @NotNull
    @Positive
    private BigDecimal amount;

    private String currencyCode;

    @NotBlank
    private String fundingSource;

    @NotBlank
    private String creditReason;

    @NotBlank
    private String reference;

    @NotBlank
    private String idempotencyKey;

    private String transactionType;
    private String description;
    private String note;
    private String approvalStatus;
}
