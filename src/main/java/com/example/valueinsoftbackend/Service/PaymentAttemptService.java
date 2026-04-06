package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbBillingWriteModels;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class PaymentAttemptService {

    private final DbBillingWriteModels dbBillingWriteModels;

    public PaymentAttemptService(DbBillingWriteModels dbBillingWriteModels) {
        this.dbBillingWriteModels = dbBillingWriteModels;
    }

    public long ensureCreatedAttempt(long invoiceId,
                                     String providerCode,
                                     String externalOrderId,
                                     BigDecimal requestedAmount,
                                     String currencyCode,
                                     String requestPayloadJson,
                                     String providerResponseJson) {
        Long existingId = dbBillingWriteModels.findPaymentAttemptId(providerCode, externalOrderId);
        if (existingId != null) {
            return existingId;
        }

        return dbBillingWriteModels.createPaymentAttempt(
                invoiceId,
                providerCode,
                externalOrderId,
                "created",
                requestedAmount,
                currencyCode,
                requestPayloadJson,
                providerResponseJson
        );
    }

    public void markCheckoutRequested(String providerCode, String externalOrderId, String providerResponseJson) {
        dbBillingWriteModels.updatePaymentAttemptCheckoutRequest(
                providerCode,
                externalOrderId,
                "checkout_requested",
                providerResponseJson
        );
    }

    public void markSucceeded(String providerCode,
                              String externalOrderId,
                              String providerResponseJson,
                              String externalPaymentReference) {
        dbBillingWriteModels.completePaymentAttempt(
                providerCode,
                externalOrderId,
                "succeeded",
                providerResponseJson,
                externalPaymentReference,
                null,
                null
        );
    }

    public void markFailed(String providerCode,
                           String externalOrderId,
                           String providerResponseJson,
                           String failureCode,
                           String failureMessage,
                           String externalPaymentReference) {
        dbBillingWriteModels.completePaymentAttempt(
                providerCode,
                externalOrderId,
                "failed",
                providerResponseJson,
                externalPaymentReference,
                failureCode,
                failureMessage
        );
    }
}
