package com.example.valueinsoftbackend.Model.PlatformAdmin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.sql.Timestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformBillingManualActionResponse {
    private long billingInvoiceId;
    private Long billingPaymentAttemptId;
    private Long branchSubscriptionId;
    private Integer branchId;
    private int tenantId;
    private String actionType;
    private String invoiceStatus;
    private String branchSubscriptionStatus;
    private BigDecimal amount;
    private BigDecimal dueAmount;
    private String currencyCode;
    private String providerCode;
    private String reference;
    private Timestamp processedAt;
}
