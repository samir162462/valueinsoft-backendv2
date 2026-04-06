package com.example.valueinsoftbackend.Model.PlatformAdmin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.sql.Timestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformBillingInvoiceItem {
    private long billingInvoiceId;
    private int tenantId;
    private int companyId;
    private String companyName;
    private Integer branchId;
    private String branchName;
    private Long branchSubscriptionId;
    private String invoiceNumber;
    private String status;
    private BigDecimal totalAmount;
    private BigDecimal dueAmount;
    private String currencyCode;
    private String sourceType;
    private String sourceId;
    private Timestamp issuedAt;
    private Timestamp dueAt;
    private Timestamp paidAt;
}
