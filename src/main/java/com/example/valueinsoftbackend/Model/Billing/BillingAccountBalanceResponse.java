package com.example.valueinsoftbackend.Model.Billing;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.sql.Timestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillingAccountBalanceResponse {
    private int companyId;
    private long billingAccountId;
    private String currencyCode;
    private BigDecimal availableBalance;
    private String status;
    private long version;
    private Timestamp updatedAt;
}
