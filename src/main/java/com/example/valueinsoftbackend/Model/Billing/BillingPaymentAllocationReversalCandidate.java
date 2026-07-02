package com.example.valueinsoftbackend.Model.Billing;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillingPaymentAllocationReversalCandidate {
    private long billingPaymentId;
    private long billingPaymentAllocationId;
    private long billingInvoiceId;
    private long billingAccountId;
    private String paymentSource;
    private String providerCode;
    private String providerReference;
    private BigDecimal allocatedAmount;
    private BigDecimal reversibleAmount;
    private String currencyCode;
}
