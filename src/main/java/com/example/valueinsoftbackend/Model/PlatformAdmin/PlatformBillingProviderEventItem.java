package com.example.valueinsoftbackend.Model.PlatformAdmin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformBillingProviderEventItem {
    private long billingProviderEventId;
    private String providerCode;
    private String providerEventId;
    private String eventType;
    private String externalReference;
    private String processingStatus;
    private Long attemptId;
    private Long billingInvoiceId;
    private Integer companyId;
    private String errorMessage;
    private Timestamp receivedAt;
    private Timestamp processedAt;
}
