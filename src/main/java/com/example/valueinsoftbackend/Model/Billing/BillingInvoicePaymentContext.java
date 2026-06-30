package com.example.valueinsoftbackend.Model.Billing;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillingInvoicePaymentContext {
    private long billingInvoiceId;
    private long billingAccountId;
    private Integer tenantId;
    private int companyId;
    private Long branchSubscriptionId;
    private Integer branchId;
    private String invoiceStatus;
    private String currencyCode;
    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private BigDecimal dueAmount;
    private BigDecimal availableBalance;
}
