package com.example.valueinsoftbackend.Model.Billing;

import java.math.BigDecimal;

public class BillingPaymentAttemptValidationContext {

    private final long billingPaymentAttemptId;
    private final long billingInvoiceId;
    private final Integer companyId;
    private final Integer branchId;
    private final BigDecimal requestedAmount;
    private final String currencyCode;
    private final String status;
    private final String externalPaymentReference;

    public BillingPaymentAttemptValidationContext(long billingPaymentAttemptId,
                                                  long billingInvoiceId,
                                                  Integer companyId,
                                                  Integer branchId,
                                                  BigDecimal requestedAmount,
                                                  String currencyCode,
                                                  String status,
                                                  String externalPaymentReference) {
        this.billingPaymentAttemptId = billingPaymentAttemptId;
        this.billingInvoiceId = billingInvoiceId;
        this.companyId = companyId;
        this.branchId = branchId;
        this.requestedAmount = requestedAmount;
        this.currencyCode = currencyCode;
        this.status = status;
        this.externalPaymentReference = externalPaymentReference;
    }

    public long getBillingPaymentAttemptId() {
        return billingPaymentAttemptId;
    }

    public long getBillingInvoiceId() {
        return billingInvoiceId;
    }

    public Integer getCompanyId() {
        return companyId;
    }

    public Integer getBranchId() {
        return branchId;
    }

    public BigDecimal getRequestedAmount() {
        return requestedAmount;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public String getStatus() {
        return status;
    }

    public String getExternalPaymentReference() {
        return externalPaymentReference;
    }
}
