package com.example.valueinsoftbackend.Model.Billing;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.sql.Timestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillingOverdueInvoiceCandidate {
    private long billingInvoiceId;
    private long billingAccountId;
    private long branchSubscriptionId;
    private int tenantId;
    private int companyId;
    private String companyName;
    private int branchId;
    private String branchName;
    private String providerCode;
    private String currencyCode;
    private BigDecimal totalAmount;
    private BigDecimal dueAmount;
    private Timestamp dueAt;
    private int existingDunningAttempts;
}
