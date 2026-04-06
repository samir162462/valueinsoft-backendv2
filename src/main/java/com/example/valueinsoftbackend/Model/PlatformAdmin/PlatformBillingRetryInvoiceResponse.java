package com.example.valueinsoftbackend.Model.PlatformAdmin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.sql.Timestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformBillingRetryInvoiceResponse {
    private long billingInvoiceId;
    private long billingPaymentAttemptId;
    private String providerCode;
    private String externalOrderId;
    private String checkoutUrl;
    private BigDecimal requestedAmount;
    private String currencyCode;
    private Timestamp generatedAt;
}
