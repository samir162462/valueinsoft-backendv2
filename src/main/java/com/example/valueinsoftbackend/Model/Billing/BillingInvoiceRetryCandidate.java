package com.example.valueinsoftbackend.Model.Billing;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.sql.Timestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillingInvoiceRetryCandidate {
    private long billingInvoiceId;
    private long branchSubscriptionId;
    private long billingAccountId;
    private int tenantId;
    private int companyId;
    private int branchId;
    private String providerCode;
    private String currencyCode;
    private BigDecimal dueAmount;
    private String invoiceStatus;
    private int attemptCount;
    private Timestamp latestAttemptAt;
}
