package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbBillingWriteModels;
import org.springframework.stereotype.Service;

@Service
public class BillingProviderEventService {

    private final DbBillingWriteModels dbBillingWriteModels;

    public BillingProviderEventService(DbBillingWriteModels dbBillingWriteModels) {
        this.dbBillingWriteModels = dbBillingWriteModels;
    }

    public void recordProcessedEvent(String providerCode,
                                     String providerEventId,
                                     String eventType,
                                     String externalReference,
                                     String payloadJson) {
        dbBillingWriteModels.upsertProviderEvent(
                providerCode,
                providerEventId,
                eventType,
                externalReference,
                payloadJson,
                "processed",
                null
        );
    }

    public void recordFailedEvent(String providerCode,
                                  String providerEventId,
                                  String eventType,
                                  String externalReference,
                                  String payloadJson,
                                  String errorMessage) {
        dbBillingWriteModels.upsertProviderEvent(
                providerCode,
                providerEventId,
                eventType,
                externalReference,
                payloadJson,
                "failed",
                errorMessage
        );
    }

    public boolean isProcessedEvent(String providerCode, String providerEventId) {
        return "processed".equalsIgnoreCase(
                dbBillingWriteModels.findProviderEventStatus(providerCode, providerEventId)
        );
    }
}
