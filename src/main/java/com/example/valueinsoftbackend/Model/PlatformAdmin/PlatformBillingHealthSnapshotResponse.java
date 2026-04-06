package com.example.valueinsoftbackend.Model.PlatformAdmin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.sql.Timestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformBillingHealthSnapshotResponse {
    private Integer tenantId;
    private int openInvoices;
    private int overdueInvoices;
    private BigDecimal overdueInvoiceAmount;
    private int renewalBacklogCount;
    private int pendingRenewalEntitlements;
    private int pastDueEntitlements;
    private int retryBlockedInvoices;
    private int manualRetryCooldownMinutes;
    private int manualRetryMaxAttempts;
    private Timestamp generatedAt;
}
