package com.example.valueinsoftbackend.Model.Billing;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BranchBillingCheckoutCandidate {
    private long billingInvoiceId;
    private long branchSubscriptionId;
    private long billingAccountId;
    private int tenantId;
    private int companyId;
    private int branchId;
    private String currencyCode;
    private BigDecimal dueAmount;
    private String invoiceStatus;
    private String latestAttemptStatus;
    private String latestExternalOrderId;
}
