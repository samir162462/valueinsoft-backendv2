package com.example.valueinsoftbackend.Model.Billing;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillingBalanceTopUpResponse {
    private int companyId;
    private long billingInvoiceId;
    private Long billingPaymentAttemptId;
    private String providerCode;
    private String externalOrderId;
    private String checkoutUrl;
    private BigDecimal amount;
    private String currencyCode;
    private String status;
}
