package com.example.valueinsoftbackend.Model.Billing;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillingInvoiceMutationContext {
    private long billingInvoiceId;
    private Long branchSubscriptionId;
    private int tenantId;
    private int companyId;
    private Integer branchId;
    private String status;
    private BigDecimal totalAmount;
    private BigDecimal dueAmount;
    private String currencyCode;
}
