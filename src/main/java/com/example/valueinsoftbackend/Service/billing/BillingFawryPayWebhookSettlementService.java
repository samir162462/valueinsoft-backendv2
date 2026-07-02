package com.example.valueinsoftbackend.Service.billing;

import com.example.valueinsoftbackend.DatabaseRequests.DbBillingWriteModels;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Billing.BillingInvoicePaymentContext;
import com.example.valueinsoftbackend.Model.Billing.BillingPaymentAttemptStatus;
import com.example.valueinsoftbackend.Model.Billing.BillingPaymentAttemptValidationContext;
import com.example.valueinsoftbackend.Model.Billing.BillingProviderWebhookSettlementResponse;
import com.example.valueinsoftbackend.OnlinePayment.FawryPayProperties;
import com.example.valueinsoftbackend.Service.payment.PaymentAttemptService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class BillingFawryPayWebhookSettlementService {

    private static final String PROVIDER_CODE = "fawrypay";
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final DbBillingWriteModels dbBillingWriteModels;
    private final FawryPayProperties fawryPayProperties;
    private final PaymentAttemptService paymentAttemptService;
    private final BillingEntitlementService billingEntitlementService;
    private final ObjectMapper objectMapper;

    public BillingFawryPayWebhookSettlementService(DbBillingWriteModels dbBillingWriteModels,
                                                   FawryPayProperties fawryPayProperties,
                                                   PaymentAttemptService paymentAttemptService,
                                                   BillingEntitlementService billingEntitlementService,
                                                   ObjectMapper objectMapper) {
        this.dbBillingWriteModels = dbBillingWriteModels;
        this.fawryPayProperties = fawryPayProperties;
        this.paymentAttemptService = paymentAttemptService;
        this.billingEntitlementService = billingEntitlementService;
        this.objectMapper = objectMapper;
    }

    @Transactional(noRollbackFor = ApiException.class)
    public BillingProviderWebhookSettlementResponse settleCallback(Map<String, ?> callbackPayload) {
        Map<String, String> payload = normalizePayload(callbackPayload);
        String externalOrderId = requireExternalOrderId(first(payload, "merchantRefNumber", "merchantRefNum"));
        String providerEventId = requireProviderEventId(payload, externalOrderId);
        String payloadJson = toJson(payload);

        verifyResponseSignature(payload, externalOrderId);

        boolean reserved = dbBillingWriteModels.reserveProviderEventProcessing(
                PROVIDER_CODE,
                providerEventId,
                "payment_callback",
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
                    "FAWRYPAY_PROVIDER_EVENT_ALREADY_RESERVED",
                    "FawryPay provider event is already reserved with status: " + existingStatus
            );
        }

        BillingPaymentAttemptValidationContext attempt =
                dbBillingWriteModels.lockPaymentAttemptValidationContext(PROVIDER_CODE, externalOrderId);
        if (attempt == null) {
            dbBillingWriteModels.markProviderEventStatus(PROVIDER_CODE, providerEventId, "failed", null, null, null, "Payment attempt not found");
            throw new ApiException(HttpStatus.NOT_FOUND, "FAWRYPAY_ORDER_NOT_FOUND", "No payment attempt matches the callback order reference");
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

        String orderStatus = first(payload, "orderStatus");
        if (!isPaid(orderStatus)) {
            if (isPending(orderStatus)) {
                dbBillingWriteModels.markProviderEventStatus(PROVIDER_CODE, providerEventId, "processed", attempt.getBillingPaymentAttemptId(), attempt.getBillingInvoiceId(), invoice.getCompanyId(), "FawryPay payment is still pending");
                return response("PAYMENT_PENDING", providerEventId, externalOrderId, attempt.getBillingInvoiceId(), attempt.getBillingPaymentAttemptId(), null, null, false);
            }
            paymentAttemptService.markFailed(
                    PROVIDER_CODE,
                    externalOrderId,
                    payloadJson,
                    "FAWRYPAY_CALLBACK_REPORTED_FAILURE",
                    first(payload, "statusDescription", "orderStatus", "statusCode"),
                    providerEventId
            );
            dbBillingWriteModels.markProviderEventStatus(PROVIDER_CODE, providerEventId, "processed", attempt.getBillingPaymentAttemptId(), attempt.getBillingInvoiceId(), invoice.getCompanyId(), null);
            return response("PAYMENT_FAILED", providerEventId, externalOrderId, attempt.getBillingInvoiceId(), attempt.getBillingPaymentAttemptId(), null, null, false);
        }

        BigDecimal paymentAmount = parseAmount(first(payload, "paymentAmount", "orderAmount"), "FAWRYPAY_PAYMENT_AMOUNT_INVALID");
        validateAttemptAmount(attempt, paymentAmount);
        BigDecimal currentDue = amount(invoice.getDueAmount());
        if (currentDue.compareTo(ZERO) <= 0) {
            dbBillingWriteModels.markProviderEventStatus(PROVIDER_CODE, providerEventId, "processed", attempt.getBillingPaymentAttemptId(), attempt.getBillingInvoiceId(), invoice.getCompanyId(), "Invoice already paid");
            return response("IGNORED_ALREADY_PAID", providerEventId, externalOrderId, attempt.getBillingInvoiceId(), attempt.getBillingPaymentAttemptId(), null, null, false);
        }
        if (paymentAmount.compareTo(currentDue) > 0) {
            dbBillingWriteModels.markProviderEventStatus(PROVIDER_CODE, providerEventId, "failed", attempt.getBillingPaymentAttemptId(), attempt.getBillingInvoiceId(), invoice.getCompanyId(), "Payment amount exceeds invoice due amount");
            throw new ApiException(HttpStatus.CONFLICT, "FAWRYPAY_PAYMENT_EXCEEDS_REMAINING_DUE", "FawryPay payment exceeds the invoice remaining due amount");
        }

        paymentAttemptService.markSucceeded(PROVIDER_CODE, externalOrderId, payloadJson, providerEventId);
        long paymentId = dbBillingWriteModels.createBillingPayment(
                invoice.getCompanyId(),
                invoice.getBillingAccountId(),
                "FAWRYPAY",
                PROVIDER_CODE,
                paymentAmount,
                normalizeCurrency(invoice.getCurrencyCode()),
                "ALLOCATED",
                providerEventId,
                "provider-callback:" + PROVIDER_CODE + ":" + providerEventId,
                "{\"source\":\"fawrypay_webhook\",\"externalOrderId\":\"" + escapeJson(externalOrderId) + "\"}"
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
                "FAWRYPAY_CLEARING",
                providerEventId,
                "PENDING_SETTLEMENT",
                "{\"source\":\"fawrypay_webhook\",\"providerEventId\":\"" + escapeJson(providerEventId) +
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
                "{\"source\":\"fawrypay_webhook\",\"providerEventId\":\"" + escapeJson(providerEventId) + "\"}"
        );

        if (fullyPaid && invoice.getBranchSubscriptionId() != null && invoice.getBranchId() != null) {
            dbBillingWriteModels.updateBranchSubscriptionStatusById(
                    invoice.getBranchSubscriptionId(),
                    "active",
                    "{\"source\":\"fawrypay_webhook\",\"providerEventId\":\"" + escapeJson(providerEventId) + "\"}"
            );
            billingEntitlementService.recordManualStateChange(
                    invoice.getBranchId(),
                    invoice.getBranchSubscriptionId(),
                    invoice.getBillingInvoiceId(),
                    "pending_payment",
                    "active",
                    "fawrypay_payment_success",
                    "{\"source\":\"fawrypay_webhook\",\"providerEventId\":\"" + escapeJson(providerEventId) + "\"}"
            );
        }

        dbBillingWriteModels.markProviderEventStatus(PROVIDER_CODE, providerEventId, "processed", attempt.getBillingPaymentAttemptId(), attempt.getBillingInvoiceId(), invoice.getCompanyId(), null);
        return response("SETTLED", providerEventId, externalOrderId, invoice.getBillingInvoiceId(), attempt.getBillingPaymentAttemptId(), paymentId, allocationId, false);
    }

    private void verifyResponseSignature(Map<String, String> payload, String externalOrderId) {
        String actualSignature = first(payload, "signature");
        if (actualSignature == null || actualSignature.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FAWRYPAY_CALLBACK_SIGNATURE_MISSING", "FawryPay callback signature is missing");
        }
        if (fawryPayProperties.getSecureHashKey() == null || fawryPayProperties.getSecureHashKey().isBlank()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "FAWRYPAY_HMAC_NOT_CONFIGURED", "FawryPay secure hash key is not configured");
        }

        String expectedSignature = buildResponseSignature(payload, externalOrderId);
        if (!expectedSignature.equalsIgnoreCase(actualSignature.trim())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "FAWRYPAY_CALLBACK_SIGNATURE_INVALID", "FawryPay callback signature verification failed");
        }
    }

    String buildResponseSignature(Map<String, String> payload, String externalOrderId) {
        StringBuilder builder = new StringBuilder();
        appendIfPresent(builder, first(payload, "referenceNumber", "referenceNo", "fawryRefNumber"));
        builder.append(externalOrderId);
        appendIfPresent(builder, moneyForSignature(first(payload, "paymentAmount")));
        appendIfPresent(builder, moneyForSignature(first(payload, "orderAmount")));
        appendIfPresent(builder, first(payload, "orderStatus"));
        appendIfPresent(builder, first(payload, "paymentMethod"));
        appendIfPresent(builder, moneyForSignature(first(payload, "fawryFees")));
        appendIfPresent(builder, moneyForSignature(first(payload, "shippingFees")));
        appendIfPresent(builder, first(payload, "authNumber"));
        appendIfPresent(builder, first(payload, "customerMail", "customerEmail"));
        appendIfPresent(builder, first(payload, "customerMobile"));
        builder.append(fawryPayProperties.getSecureHashKey().trim());
        return sha256(builder.toString());
    }

    private Map<String, String> normalizePayload(Map<String, ?> payload) {
        Map<String, String> normalized = new LinkedHashMap<>();
        if (payload == null) {
            return normalized;
        }
        for (Map.Entry<String, ?> entry : payload.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            normalized.put(entry.getKey(), String.valueOf(entry.getValue()));
        }
        return normalized;
    }

    private void validateAttemptOwnership(String providerEventId,
                                          BillingPaymentAttemptValidationContext attempt,
                                          BillingInvoicePaymentContext invoice) {
        if (attempt.getCompanyId() != null && attempt.getCompanyId() != invoice.getCompanyId()) {
            dbBillingWriteModels.markProviderEventStatus(PROVIDER_CODE, providerEventId, "failed", attempt.getBillingPaymentAttemptId(), attempt.getBillingInvoiceId(), invoice.getCompanyId(), "Payment attempt company does not match invoice company");
            throw new ApiException(HttpStatus.CONFLICT, "FAWRYPAY_ATTEMPT_COMPANY_MISMATCH", "FawryPay payment attempt company does not match the billing invoice company");
        }
        if (attempt.getBranchId() != null && !attempt.getBranchId().equals(invoice.getBranchId())) {
            dbBillingWriteModels.markProviderEventStatus(PROVIDER_CODE, providerEventId, "failed", attempt.getBillingPaymentAttemptId(), attempt.getBillingInvoiceId(), invoice.getCompanyId(), "Payment attempt branch does not match invoice branch");
            throw new ApiException(HttpStatus.CONFLICT, "FAWRYPAY_ATTEMPT_BRANCH_MISMATCH", "FawryPay payment attempt branch does not match the billing invoice branch");
        }
    }

    private void validateAttemptAmount(BillingPaymentAttemptValidationContext attempt, BigDecimal paymentAmount) {
        BigDecimal expectedAmount = amount(attempt.getRequestedAmount());
        if (expectedAmount.compareTo(paymentAmount) != 0) {
            throw new ApiException(HttpStatus.CONFLICT, "FAWRYPAY_AMOUNT_MISMATCH", "FawryPay callback amount does not match the payment attempt");
        }
    }

    private BillingProviderWebhookSettlementResponse response(String status,
                                                              String providerEventId,
                                                              String externalOrderId,
                                                              Long billingInvoiceId,
                                                              Long billingPaymentAttemptId,
                                                              Long billingPaymentId,
                                                              Long billingPaymentAllocationId,
                                                              boolean duplicate) {
        return new BillingProviderWebhookSettlementResponse(
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

    private String requireProviderEventId(Map<String, String> payload, String externalOrderId) {
        String providerEventId = first(payload, "referenceNumber", "referenceNo", "fawryRefNumber");
        String eventSuffix = first(payload, "orderStatus", "statusCode");
        if (eventSuffix == null || eventSuffix.isBlank()) {
            eventSuffix = "callback";
        }
        String resolved = providerEventId == null || providerEventId.isBlank()
                ? externalOrderId + ":" + eventSuffix
                : providerEventId;
        if (resolved == null || resolved.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FAWRYPAY_PROVIDER_EVENT_ID_MISSING", "FawryPay provider event id is missing");
        }
        return resolved.trim();
    }

    private String requireExternalOrderId(String externalOrderId) {
        if (externalOrderId == null || externalOrderId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FAWRYPAY_EXTERNAL_ORDER_ID_MISSING", "FawryPay merchant reference is missing");
        }
        return externalOrderId.trim();
    }

    private boolean isPaid(String orderStatus) {
        return "paid".equalsIgnoreCase(orderStatus == null ? "" : orderStatus.trim());
    }

    private boolean isPending(String orderStatus) {
        String normalized = orderStatus == null ? "" : orderStatus.trim().toLowerCase(Locale.ROOT);
        return "new".equals(normalized) || "unpaid".equals(normalized) || "pending".equals(normalized) || "processing".equals(normalized);
    }

    private BigDecimal parseAmount(String value, String errorCode) {
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode, "FawryPay callback amount is missing");
        }
        try {
            return new BigDecimal(value.trim()).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode, "FawryPay callback amount is invalid");
        }
    }

    private String moneyForSignature(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        try {
            return new BigDecimal(value.trim()).setScale(2, RoundingMode.HALF_UP).toPlainString();
        } catch (NumberFormatException exception) {
            return value.trim();
        }
    }

    private BigDecimal amount(BigDecimal value) {
        return value == null ? ZERO : value.max(ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private String normalizeCurrency(String currencyCode) {
        return currencyCode == null || currencyCode.isBlank() ? "EGP" : currencyCode.trim().toUpperCase(Locale.ROOT);
    }

    private void appendIfPresent(StringBuilder builder, String value) {
        if (value != null && !value.isBlank()) {
            builder.append(value.trim());
        }
    }

    private String first(Map<String, String> payload, String... keys) {
        if (keys == null) {
            return "";
        }
        for (String key : keys) {
            String value = payload.get(key);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(encoded.length * 2);
            for (byte item : encoded) {
                builder.append(String.format(Locale.ROOT, "%02x", item));
            }
            return builder.toString();
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "FAWRYPAY_SIGNATURE_FAILED", "Failed to compute FawryPay callback signature");
        }
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
