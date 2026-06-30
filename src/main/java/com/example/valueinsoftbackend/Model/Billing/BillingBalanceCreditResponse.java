package com.example.valueinsoftbackend.Model.Billing;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillingBalanceCreditResponse {
    private int companyId;
    private long billingAccountId;
    private long billingAccountLedgerId;
    private BigDecimal creditedAmount;
    private String currencyCode;
    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;
    private String fundingSource;
    private String creditReason;
    private String reference;
    private String approvalStatus;
}
