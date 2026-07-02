package com.example.valueinsoftbackend.Model.Billing;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillingPaymentInitiationResponse {
    private long billingInvoiceId;
    private int companyId;
    private Integer branchId;
    private String status;
    private String invoiceStatus;
    private String currencyCode;
    private BigDecimal balanceAppliedAmount;
    private BigDecimal providerAmountDue;
    private BigDecimal remainingDueAmount;
    private BigDecimal availableBalance;
    private Long billingPaymentId;
    private Long billingPaymentAllocationId;
    private Long billingAccountLedgerId;
    private Long billingPaymentAttemptId;
    private String providerCode;
    private String externalOrderId;
    private String paymentAttemptStatus;
    private String checkoutUrl;
}
