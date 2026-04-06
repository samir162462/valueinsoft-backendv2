package com.example.valueinsoftbackend.Model.PlatformAdmin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.sql.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformBillingRenewalBacklogItem {
    private long previousBranchSubscriptionId;
    private int tenantId;
    private int companyId;
    private String companyName;
    private int branchId;
    private String branchName;
    private String priceCode;
    private String billingInterval;
    private BigDecimal unitAmount;
    private Date currentPeriodStart;
    private Date currentPeriodEnd;
}
