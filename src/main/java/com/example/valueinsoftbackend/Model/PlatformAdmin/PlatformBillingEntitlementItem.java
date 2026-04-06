package com.example.valueinsoftbackend.Model.PlatformAdmin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformBillingEntitlementItem {
    private int tenantId;
    private int companyId;
    private String companyName;
    private int branchId;
    private String branchName;
    private long branchSubscriptionId;
    private long billingInvoiceId;
    private String currentState;
    private String eventType;
    private String reasonCode;
    private Timestamp effectiveAt;
}
