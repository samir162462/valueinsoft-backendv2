package com.example.valueinsoftbackend.Model.Billing;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.sql.Timestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillingAccountLedgerItem {
    private long billingAccountLedgerId;
    private long billingAccountId;
    private int companyId;
    private String transactionType;
    private BigDecimal amount;
    private String currencyCode;
    private String direction;
    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;
    private String referenceType;
    private String referenceId;
    private String fundingSource;
    private String creditReason;
    private String approvalStatus;
    private String description;
    private Timestamp createdAt;
}
