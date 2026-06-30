package com.example.valueinsoftbackend.Service.payment;

import com.example.valueinsoftbackend.DatabaseRequests.DbBillingWriteModels;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Billing.BillingPaymentAttemptStatus;
import org.springframework.http.HttpStatus;
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
                BillingPaymentAttemptStatus.CREATED.legacyValue(),
                requestedAmount,
                currencyCode,
                requestPayloadJson,
                providerResponseJson
        );
    }

    public void markCheckoutPending(String providerCode, String externalOrderId, String providerResponseJson) {
        int rows = dbBillingWriteModels.updatePaymentAttemptCheckoutRequest(
                providerCode,
                externalOrderId,
                BillingPaymentAttemptStatus.CHECKOUT_PENDING.legacyValue(),
                providerResponseJson
        );
        requireUpdated(rows);
    }

    public void markCheckoutRequested(String providerCode, String externalOrderId, String providerResponseJson) {
        int rows = dbBillingWriteModels.updatePaymentAttemptCheckoutRequest(
                providerCode,
                externalOrderId,
                BillingPaymentAttemptStatus.CHECKOUT_REQUESTED.legacyValue(),
                providerResponseJson
        );
        requireUpdated(rows);
    }

    public void markSucceeded(String providerCode,
                              String externalOrderId,
                              String providerResponseJson,
                              String externalPaymentReference) {
        int rows = dbBillingWriteModels.completePaymentAttempt(
                providerCode,
                externalOrderId,
                BillingPaymentAttemptStatus.SUCCEEDED.legacyValue(),
                providerResponseJson,
                externalPaymentReference,
                null,
                null
        );
        requireUpdated(rows);
    }

    public void markFailed(String providerCode,
                           String externalOrderId,
                           String providerResponseJson,
                           String failureCode,
                           String failureMessage,
                           String externalPaymentReference) {
        int rows = dbBillingWriteModels.completePaymentAttempt(
                providerCode,
                externalOrderId,
                BillingPaymentAttemptStatus.FAILED.legacyValue(),
                providerResponseJson,
                externalPaymentReference,
                failureCode,
                failureMessage
        );
        requireUpdated(rows);
    }

    private void requireUpdated(int rows) {
        if (rows > 0) {
            return;
        }
        throw new ApiException(
                HttpStatus.CONFLICT,
                "BILLING_PAYMENT_ATTEMPT_TERMINAL_OR_NOT_FOUND",
                "Payment attempt is terminal or was not found"
        );
    }
}
