package com.example.valueinsoftbackend.Model.PlatformAdmin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.sql.Timestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformBillingPaymentItem {
    private long billingPaymentId;
    private int tenantId;
    private int companyId;
    private String companyName;
    private Long billingAccountId;
    private String paymentSource;
    private String providerCode;
    private BigDecimal amount;
    private String currencyCode;
    private String status;
    private String providerReference;
    private Long billingInvoiceId;
    private BigDecimal allocatedAmount;
    private BigDecimal providerGrossAmount;
    private BigDecimal providerFeeAmount;
    private BigDecimal providerNetAmount;
    private String settlementCurrencyCode;
    private String settlementDestination;
    private String providerSettlementReference;
    private String reconciliationStatus;
    private Timestamp reconciledAt;
    private Timestamp createdAt;
}
