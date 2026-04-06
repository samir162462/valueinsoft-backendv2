package com.example.valueinsoftbackend.Model.Billing;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.sql.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillingRenewalCandidate {
    private long billingAccountId;
    private long previousBranchSubscriptionId;
    private int tenantId;
    private int companyId;
    private String companyName;
    private int branchId;
    private String branchName;
    private String currencyCode;
    private String priceCode;
    private String billingInterval;
    private BigDecimal unitAmount;
    private Date currentPeriodStart;
    private Date currentPeriodEnd;
}
