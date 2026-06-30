package com.example.valueinsoftbackend.Model.Billing;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillingPaymentPreviewResponse {
    private long billingInvoiceId;
    private int companyId;
    private Integer branchId;
    private String invoiceStatus;
    private String currencyCode;
    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private BigDecimal dueAmount;
    private BigDecimal availableBalance;
    private BigDecimal balanceAppliedAmount;
    private BigDecimal providerAmountDue;
    private String paymentStrategy;
}
