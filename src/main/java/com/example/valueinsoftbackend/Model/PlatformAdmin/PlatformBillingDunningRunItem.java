package com.example.valueinsoftbackend.Model.PlatformAdmin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformBillingDunningRunItem {
    private long billingDunningRunId;
    private long billingInvoiceId;
    private int tenantId;
    private int companyId;
    private String companyName;
    private int branchId;
    private String branchName;
    private String status;
    private int attemptNumber;
    private Timestamp scheduledAt;
    private Timestamp executedAt;
    private String resultSummary;
}
