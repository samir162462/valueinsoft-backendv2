package com.example.valueinsoftbackend.Model.Billing;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillingPaymentAttemptSnapshot {
    private long billingPaymentAttemptId;
    private String providerCode;
    private String externalOrderId;
    private String status;
    private BigDecimal requestedAmount;
    private String currencyCode;
    private String checkoutUrl;
}
