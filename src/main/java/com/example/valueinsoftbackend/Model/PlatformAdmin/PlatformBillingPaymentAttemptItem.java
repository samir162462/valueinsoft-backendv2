package com.example.valueinsoftbackend.Model.PlatformAdmin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.sql.Timestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformBillingPaymentAttemptItem {
    private long billingPaymentAttemptId;
    private long billingInvoiceId;
    private int tenantId;
    private int companyId;
    private String companyName;
    private Integer branchId;
    private String branchName;
    private String providerCode;
    private String externalOrderId;
    private String externalPaymentReference;
    private String status;
    private BigDecimal requestedAmount;
    private String currencyCode;
    private Timestamp attemptedAt;
    private Timestamp completedAt;
}
