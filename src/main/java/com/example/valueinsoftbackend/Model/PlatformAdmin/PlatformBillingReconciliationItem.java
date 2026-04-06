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
    private int legacySubscriptionId;
    private Date startTime;
    private Date endTime;
    private BigDecimal amountToPay;
    private BigDecimal amountPaid;
    private Integer legacyOrderId;
    private String legacyStatus;
    private Long mirroredBranchSubscriptionId;
    private String mirroredBranchSubscriptionStatus;
    private Long mirroredInvoiceId;
    private String mirroredInvoiceStatus;
    private Long mirroredPaymentAttemptId;
    private String mirroredPaymentAttemptStatus;
    private String mirroredProviderCode;
    private String reconciliationStatus;
}
