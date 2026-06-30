package com.example.valueinsoftbackend.Model.Billing;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillingBalanceSettlementSnapshot {
    private long billingPaymentId;
    private Long billingPaymentAllocationId;
    private Long billingAccountLedgerId;
    private BigDecimal amount;
    private String currencyCode;
}
