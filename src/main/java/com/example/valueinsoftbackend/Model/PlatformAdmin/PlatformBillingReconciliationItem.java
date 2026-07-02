package com.example.valueinsoftbackend.Model.PlatformAdmin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.sql.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformBillingReconciliationItem {
    private int tenantId;
    private int companyId;
    private String companyName;
    private int branchId;
    private String branchName;
    private Long branchSubscriptionId;
    private Long billingInvoiceId;
    private String invoiceNumber;
    private Date currentPeriodStart;
    private Date currentPeriodEnd;
    private String subscriptionStatus;
    private String invoiceStatus;
    private Long latestPaymentAttemptId;
    private String latestPaymentAttemptStatus;
    private String providerCode;
    private BigDecimal totalAmount;
    private BigDecimal amountPaid;
    private BigDecimal dueAmount;
    private BigDecimal allocatedAmount;
    private String reconciliationStatus;
}
