package com.example.valueinsoftbackend.Model.PlatformAdmin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.sql.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformBillingSubscriptionItem {
    private int subscriptionId;
    private Long billingInvoiceId;
    private int tenantId;
    private int companyId;
    private String companyName;
    private int branchId;
    private String branchName;
    private String packageId;
    private String packageDisplayName;
    private Date startTime;
    private Date endTime;
    private BigDecimal amountToPay;
    private BigDecimal amountPaid;
    private BigDecimal outstandingAmount;
    private String status;
    private boolean active;
}
