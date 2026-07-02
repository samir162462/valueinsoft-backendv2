package com.example.valueinsoftbackend.Service.billing;

import com.example.valueinsoftbackend.DatabaseRequests.DbBillingWriteModels;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Billing.BillingInvoicePaymentContext;
import com.example.valueinsoftbackend.Model.Billing.BillingPaymentAttemptStatus;
import com.example.valueinsoftbackend.Model.Billing.BillingPaymentAttemptValidationContext;
import com.example.valueinsoftbackend.Model.Billing.BillingPaymobWebhookSettlementResponse;
import com.example.valueinsoftbackend.Model.Request.PayMobTransactionCallbackRequest;
import com.example.valueinsoftbackend.OnlinePayment.OPModel.TransactionProcessedCallback;
import com.example.valueinsoftbackend.Service.PayMobService;
import com.example.valueinsoftbackend.Service.payment.PaymentAttemptService;
import com.example.valueinsoftbackend.Service.payment.PaymentProviderFinanceIntegrationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Locale;

@Service
public class BillingPaymobWebhookSettlementService {

    private static final String PROVIDER_CODE = "paymob";
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final DbBillingWriteModels dbBillingWriteModels;
    private final PayMobService payMobService;
    private final PaymentAttemptService paymentAttemptService;
    private final BillingEntitlementService billingEntitlementService;
    private final PaymentProviderFinanceIntegrationService paymentProviderFinanceIntegrationService;
    private final ObjectMapper objectMapper;

    public BillingPaymobWebhookSettlementService(DbBillingWriteModels dbBillingWriteModels,
                                                 PayMobService payMobService,
                                                 PaymentAttemptService paymentAttemptService,
                                                 BillingEntitlementService billingEntitlementService,
                                                 PaymentProviderFinanceIntegrationService paymentProviderFinanceIntegrationService,
                                                 ObjectMapper objectMapper) {
        this.dbBillingWriteModels = dbBillingWriteModels;
        this.payMobService = payMobService;
        this.paymentAttemptService = paymentAttemptService;
        this.billingEntitlementService = billingEntitlementService;
        this.paymentProviderFinanceIntegrationService = paymentProviderFinanceIntegrationService;
        this.objectMapper = objectMapper;
    }

    @Transactional(noRollbackFor = ApiException.class)
    public BillingPaymobWebhookSettlementResponse settleTransactionCallback(PayMobTransactionCallbackRequest request) {
        TransactionProcessedCallback callback = payMobService.parseVerifiedCallbackWithoutAttemptValidation(request);
        String providerEventId = requireProviderEventId(payMobService.getProviderEventId(request));
        String externalOrderId = requireExternalOrderId(payMobService.getExternalOrderId(request));
        String payloadJson = toJson(request);

        boolean reserved = dbBillingWriteModels.reserveProviderEventProcessing(
                PROVIDER_CODE,
                providerEventId,
                "transaction_callback",
                externalOrderId,
                payloadJson
        );
        if (!reserved) {
            String existingStatus = dbBillingWriteModels.findProviderEventStatus(PROVIDER_CODE, providerEventId);
            if ("processed".equalsIgnoreCase(existingStatus)) {
                return response("DUPLICATE_PROCESSED", providerEventId, externalOrderId, null, null, null, null, true);
            }
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "PAYMOB_PROVIDER_EVENT_ALREADY_RESERVED",
                    "Paymob provider event is already reserved with status: " + existingStatus
            );
        }

        BillingPaymentAttemptValidationContext attempt =
                dbBillingWriteModels.lockPaymentAttemptValidationContext(PROVIDER_CODE, externalOrderId);
        if (attempt == null) {
            dbBillingWriteModels.markProviderEventStatus(PROVIDER_CODE, providerEventId, "failed", null, null, null, "Payment attempt not found");
            throw new ApiException(HttpStatus.NOT_FOUND, "PAYMOB_ORDER_NOT_FOUND", "No payment attempt matches the callback order reference");
        }

        try {
            payMobService.validateCallbackAgainstAttempt(request, attempt);
        } catch (ApiException exception) {
            dbBillingWriteModels.markProviderEventStatus(
                    PROVIDER_CODE,
                    providerEventId,
                    "failed",
                    attempt.getBillingPaymentAttemptId(),
                    attempt.getBillingInvoiceId(),
                    null,
                    exception.getMessage()
            );
            throw exception;
        }

        BillingInvoicePaymentContext invoice = dbBillingWriteModels.lockInvoicePaymentContext(attempt.getBillingInvoiceId());
        if (invoice == null) {
            dbBillingWriteModels.markProviderEventStatus(PROVIDER_CODE, providerEventId, "failed", attempt.getBillingPaymentAttemptId(), attempt.getBillingInvoiceId(), null, "Billing invoice not found");
            throw new ApiException(HttpStatus.NOT_FOUND, "BILLING_INVOICE_NOT_FOUND", "Billing invoice not found");
        }

        validateAttemptOwnership(providerEventId, attempt, invoice);

        if (BillingPaymentAttemptStatus.isTerminal(attempt.getStatus())) {
            dbBillingWriteModels.markProviderEventStatus(PROVIDER_CODE, providerEventId, "processed", attempt.getBillingPaymentAttemptId(), attempt.getBillingInvoiceId(), invoice.getCompanyId(), "Terminal attempt ignored");
            return response("IGNORED_TERMINAL_ATTEMPT", providerEventId, externalOrderId, attempt.getBillingInvoiceId(), attempt.getBillingPaymentAttemptId(), null, null, false);
        }

        if (!callback.isSuccess()) {
            paymentAttemptService.markFailed(
                    PROVIDER_CODE,
                    externalOrderId,
                    payloadJson,
                    "PAYMOB_CALLBACK_REPORTED_FAILURE",
                    "Provider callback marked the transaction as not successful",
                    providerEventId
            );
            dbBillingWriteModels.markProviderEventStatus(PROVIDER_CODE, providerEventId, "processed", attempt.getBillingPaymentAttemptId(), attempt.getBillingInvoiceId(), invoice.getCompanyId(), null);
            return response("PAYMENT_FAILED", providerEventId, externalOrderId, attempt.getBillingInvoiceId(), attempt.getBillingPaymentAttemptId(), null, null, false);
        }

        BigDecimal paymentAmount = centsToMoney(callback.getAmount_cents());
        BigDecimal currentDue = amount(invoice.getDueAmount());
        if (currentDue.compareTo(ZERO) <= 0) {
            dbBillingWriteModels.markProviderEventStatus(PROVIDER_CODE, providerEventId, "processed", attempt.getBillingPaymentAttemptId(), attempt.getBillingInvoiceId(), invoice.getCompanyId(), "Invoice already paid");
            return response("IGNORED_ALREADY_PAID", providerEventId, externalOrderId, attempt.getBillingInvoiceId(), attempt.getBillingPaymentAttemptId(), null, null, false);
        }
        if (paymentAmount.compareTo(currentDue) > 0) {
            dbBillingWriteModels.markProviderEventStatus(PROVIDER_CODE, providerEventId, "failed", attempt.getBillingPaymentAttemptId(), attempt.getBillingInvoiceId(), invoice.getCompanyId(), "Payment amount exceeds invoice due amount");
            throw new ApiException(HttpStatus.CONFLICT, "PAYMOB_PAYMENT_EXCEEDS_REMAINING_DUE", "Paymob payment exceeds the invoice remaining due amount");
        }

        paymentAttemptService.markSucceeded(PROVIDER_CODE, externalOrderId, payloadJson, providerEventId);

        long paymentId = dbBillingWriteModels.createBillingPayment(
                invoice.getCompanyId(),
                invoice.getBillingAccountId(),
                "PAYMOB",
                PROVIDER_CODE,
                paymentAmount,
                normalizeCurrency(invoice.getCurrencyCode()),
                "ALLOCATED",
                providerEventId,
                "provider-callback:" + PROVIDER_CODE + ":" + providerEventId,
                "{\"source\":\"paymob_webhook\",\"externalOrderId\":\"" + escapeJson(externalOrderId) + "\"}"
        );
        long allocationId = dbBillingWriteModels.createBillingPaymentAllocation(
                paymentId,
                invoice.getBillingInvoiceId(),
                paymentAmount,
                normalizeCurrency(invoice.getCurrencyCode())
        );
        dbBillingWriteModels.updateBillingPaymentReconciliationFields(
                paymentId,
                paymentAmount,
                ZERO,
                paymentAmount,
                normalizeCurrency(invoice.getCurrencyCode()),
                "PAYMOB_CLEARING",
                providerEventId,
                "PENDING_SETTLEMENT",
                "{\"source\":\"paymob_webhook\",\"providerEventId\":\"" + escapeJson(providerEventId) +
                        "\",\"externalOrderId\":\"" + escapeJson(externalOrderId) + "\"}"
        );

        BigDecimal nextPaid = amount(invoice.getPaidAmount()).add(paymentAmount).setScale(2, RoundingMode.HALF_UP);
        BigDecimal nextDue = currentDue.subtract(paymentAmount).max(ZERO).setScale(2, RoundingMode.HALF_UP);
        boolean fullyPaid = nextDue.compareTo(ZERO) == 0;
        dbBillingWriteModels.updateInvoicePaymentProjection(
                invoice.getBillingInvoiceId(),
                fullyPaid ? "paid" : "open",
                nextPaid,
                nextDue,
                fullyPaid ? Instant.now() : null,
                "{\"source\":\"paymob_webhook\",\"providerEventId\":\"" + escapeJson(providerEventId) + "\"}"
        );

        if (fullyPaid && invoice.getBranchSubscriptionId() != null && invoice.getBranchId() != null) {
            dbBillingWriteModels.updateBranchSubscriptionStatusById(
                    invoice.getBranchSubscriptionId(),
                    "active",
                    "{\"source\":\"paymob_webhook\",\"providerEventId\":\"" + escapeJson(providerEventId) + "\"}"
            );
            billingEntitlementService.recordManualStateChange(
                    invoice.getBranchId(),
                    invoice.getBranchSubscriptionId(),
                    invoice.getBillingInvoiceId(),
                    "pending_payment",
                    "active",
                    "paymob_payment_success",
                    "{\"source\":\"paymob_webhook\",\"providerEventId\":\"" + escapeJson(providerEventId) + "\"}"
            );
        }

        paymentProviderFinanceIntegrationService.enqueuePayMobSettlement(PROVIDER_CODE, externalOrderId, providerEventId, request);
        dbBillingWriteModels.markProviderEventStatus(PROVIDER_CODE, providerEventId, "processed", attempt.getBillingPaymentAttemptId(), attempt.getBillingInvoiceId(), invoice.getCompanyId(), null);
        return response("SETTLED", providerEventId, externalOrderId, invoice.getBillingInvoiceId(), attempt.getBillingPaymentAttemptId(), paymentId, allocationId, false);
    }

    private void validateAttemptOwnership(String providerEventId,
                                          BillingPaymentAttemptValidationContext attempt,
                                          BillingInvoicePaymentContext invoice) {
        if (attempt.getCompanyId() != null && attempt.getCompanyId() != invoice.getCompanyId()) {
            dbBillingWriteModels.markProviderEventStatus(
                    PROVIDER_CODE,
                    providerEventId,
                    "failed",
                    attempt.getBillingPaymentAttemptId(),
                    attempt.getBillingInvoiceId(),
                    invoice.getCompanyId(),
                    "Payment attempt company does not match invoice company"
            );
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "PAYMOB_ATTEMPT_COMPANY_MISMATCH",
                    "Paymob payment attempt company does not match the billing invoice company"
            );
        }
        if (attempt.getBranchId() != null && !attempt.getBranchId().equals(invoice.getBranchId())) {
            dbBillingWriteModels.markProviderEventStatus(
                    PROVIDER_CODE,
                    providerEventId,
                    "failed",
                    attempt.getBillingPaymentAttemptId(),
                    attempt.getBillingInvoiceId(),
                    invoice.getCompanyId(),
                    "Payment attempt branch does not match invoice branch"
            );
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "PAYMOB_ATTEMPT_BRANCH_MISMATCH",
                    "Paymob payment attempt branch does not match the billing invoice branch"
            );
        }
    }

    private BillingPaymobWebhookSettlementResponse response(String status,
                                                            String providerEventId,
                                                            String externalOrderId,
                                                            Long billingInvoiceId,
                                                            Long billingPaymentAttemptId,
                                                            Long billingPaymentId,
                                                            Long billingPaymentAllocationId,
                                                            boolean duplicate) {
        return new BillingPaymobWebhookSettlementResponse(
                status,
                PROVIDER_CODE,
                providerEventId,
                externalOrderId,
                billingInvoiceId,
                billingPaymentAttemptId,
                billingPaymentId,
                billingPaymentAllocationId,
                duplicate
        );
    }

    private String requireProviderEventId(String providerEventId) {
        if (providerEventId == null || providerEventId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYMOB_PROVIDER_EVENT_ID_MISSING", "Paymob provider event id is missing");
        }
        return providerEventId.trim();
    }

    private String requireExternalOrderId(String externalOrderId) {
        if (externalOrderId == null || externalOrderId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYMOB_EXTERNAL_ORDER_ID_MISSING", "Paymob external order id is missing");
        }
        return externalOrderId.trim();
    }

    private BigDecimal centsToMoney(int amountCents) {
        return BigDecimal.valueOf(amountCents, 2).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal amount(BigDecimal value) {
        return value == null ? ZERO : value.max(ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private String normalizeCurrency(String currencyCode) {
        return currencyCode == null || currencyCode.isBlank() ? "EGP" : currencyCode.trim().toUpperCase(Locale.ROOT);
    }

    private String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }
}
