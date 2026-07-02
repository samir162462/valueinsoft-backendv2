package com.example.valueinsoftbackend.Model.Billing;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillingProviderCheckoutOutboxItem {
    private long checkoutOutboxId;
    private long billingPaymentAttemptId;
    private long billingInvoiceId;
    private int companyId;
    private Integer branchId;
    private String providerCode;
    private String operationType;
    private String idempotencyKey;
    private BigDecimal requestedAmount;
    private String currencyCode;
    private String checkoutReference;
    private String requestPayloadJson;
    private int attemptCount;
}
