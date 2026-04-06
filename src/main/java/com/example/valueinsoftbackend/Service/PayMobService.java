package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbBillingWriteModels;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Billing.BillingPaymentAttemptValidationContext;
import com.example.valueinsoftbackend.Model.Request.PaymentTokenRequest;
import com.example.valueinsoftbackend.Model.Request.PayMobTransactionCallbackRequest;
import com.example.valueinsoftbackend.OnlinePayment.OPModel.Billing_data;
import com.example.valueinsoftbackend.OnlinePayment.OPModel.PaymentKeyRequest;
import com.example.valueinsoftbackend.OnlinePayment.OPModel.TransactionProcessedCallback;
import com.example.valueinsoftbackend.OnlinePayment.PayMobProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpStatusCodeException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

@Service
@Slf4j
public class PayMobService {

    private final PayMobProperties payMobProperties;
    private final DbBillingWriteModels dbBillingWriteModels;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    public PayMobService(PayMobProperties payMobProperties,
                         DbBillingWriteModels dbBillingWriteModels,
                         ObjectMapper objectMapper) {
        this.payMobProperties = payMobProperties;
        this.dbBillingWriteModels = dbBillingWriteModels;
        this.objectMapper = objectMapper;
    }

    public String createAuthToken() {
        validateApiConfiguration();

        String url = buildPayMobUrl("/api/auth/tokens");
        Map<String, Object> payload = new HashMap<>();
        payload.put("api_key", payMobProperties.getAuthToken());
        log.info("Requesting PayMob auth token");
        ResponseEntity<String> response = executeJsonPost(url, payload, "PAYMOB_AUTH_FAILED", "PayMob authentication request failed");

        if (response.getStatusCode() == HttpStatus.CREATED && response.getBody() != null) {
            return extractString(response.getBody(), "token", "PAYMOB_AUTH_FAILED");
        }

        throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYMOB_AUTH_FAILED", "PayMob authentication request failed");
    }

    public int createPayMobOrder(int merchantOrderId, int branchId, BigDecimal amountToPay) {
        validateApiConfiguration();
        String url = buildPayMobUrl("/api/ecommerce/orders");
        Map<String, Object> payload = new HashMap<>();
        payload.put("auth_token", createAuthToken());
        payload.put("delivery_needed", "false");
        payload.put("amount_cents", toAmountCents(amountToPay));
        payload.put("currency", "EGP");
        payload.put("merchant_order_id", merchantOrderId);
        payload.put("items", Collections.emptyList());

        log.info("Registering PayMob order for merchantOrderId={} branchId={} amount={}", merchantOrderId, branchId, amountToPay);
        ResponseEntity<String> response = executeJsonPost(url, payload, "PAYMOB_ORDER_REGISTRATION_FAILED", "PayMob order registration failed for branch " + branchId);

        if (response.getStatusCode() == HttpStatus.CREATED && response.getBody() != null) {
            return extractInt(response.getBody(), "id", "PAYMOB_ORDER_REGISTRATION_FAILED");
        }

        throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYMOB_ORDER_REGISTRATION_FAILED", "PayMob order registration failed for branch " + branchId);
    }

    public String createPaymentKeyUrl(PaymentTokenRequest request) {
        validateApiConfiguration();
        Billing_data billingData = new Billing_data(
                String.valueOf(request.getBranchId()),
                String.valueOf(request.getCompanyId()),
                request.getBranchId() + String.valueOf(request.getCompanyId()) + "@VLS.com",
                request.getBranchId() + String.valueOf(request.getCompanyId())
        );

        PaymentKeyRequest paymentKeyRequest = new PaymentKeyRequest(
                createAuthToken(),
                String.valueOf(request.getAmountCents()),
                3600,
                String.valueOf(request.getOrderId()),
                billingData,
                request.getCurrency().trim(),
                payMobProperties.getCardIntegrationId(),
                "false"
        );

        String url = buildPayMobUrl("/api/acceptance/payment_keys");
        log.info(
                "Requesting PayMob payment key for providerOrderId={} branchId={} companyId={} amountCents={} currency={}",
                request.getOrderId(),
                request.getBranchId(),
                request.getCompanyId(),
                request.getAmountCents(),
                request.getCurrency()
        );
        ResponseEntity<String> response = executeJsonPost(url, paymentKeyRequest, "PAYMOB_PAYMENT_KEY_FAILED", "PayMob payment key request failed");

        if (response.getStatusCode() == HttpStatus.CREATED && response.getBody() != null) {
            String requestId = extractString(response.getBody(), "token", "PAYMOB_PAYMENT_KEY_FAILED");
            return buildPayMobUrl("/api/acceptance/iframes/" + payMobProperties.getCardIFrameId()) +
                    "?payment_token=" + requestId;
        }

        throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYMOB_PAYMENT_KEY_FAILED", "PayMob payment key request failed");
    }

    public TransactionProcessedCallback parseCallback(PayMobTransactionCallbackRequest request) {
        validateCallbackConfiguration();
        verifyCallbackHmac(request);
        validateCallbackTransaction(request);

        PayMobTransactionCallbackRequest.TransactionPayload transaction = request.getTransaction();
        TransactionProcessedCallback callback = new TransactionProcessedCallback(
                transaction.getId(),
                transaction.getPending(),
                transaction.getAmountCents(),
                transaction.getSuccess(),
                transaction.getAuth(),
                transaction.getCapture(),
                transaction.getStandalonePayment(),
                transaction.getVoided(),
                transaction.getRefunded(),
                transaction.getOrder().getId()
        );
        log.info(
                "Validated PayMob callback providerEventId={} providerOrderId={} success={} pending={}",
                callback.getOrder_id(),
                callback.getSubId(),
                callback.isSuccess(),
                callback.isPending()
        );
        return callback;
    }

    public String getProviderEventId(PayMobTransactionCallbackRequest request) {
        return request == null || request.getTransaction() == null || request.getTransaction().getId() == null
                ? null
                : String.valueOf(request.getTransaction().getId());
    }

    public String getExternalOrderId(PayMobTransactionCallbackRequest request) {
        return request == null || request.getTransaction() == null || request.getTransaction().getOrder() == null || request.getTransaction().getOrder().getId() == null
                ? null
                : String.valueOf(request.getTransaction().getOrder().getId());
    }

    private String toAmountCents(BigDecimal amount) {
        return amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    private void validateApiConfiguration() {
        if (payMobProperties.getBaseUrl() == null || payMobProperties.getBaseUrl().isBlank()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "PAYMOB_NOT_CONFIGURED", "PayMob base URL is not configured");
        }
        if (payMobProperties.getAuthToken() == null || payMobProperties.getAuthToken().isBlank()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "PAYMOB_NOT_CONFIGURED", "PayMob auth token is not configured");
        }
        if (payMobProperties.getCardIntegrationId() <= 0) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "PAYMOB_NOT_CONFIGURED", "PayMob card integration id is not configured");
        }
        if (payMobProperties.getCardIFrameId() <= 0) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "PAYMOB_NOT_CONFIGURED", "PayMob card iframe id is not configured");
        }
    }

    private void validateCallbackConfiguration() {
        if (payMobProperties.getHmacSecret() == null || payMobProperties.getHmacSecret().isBlank()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "PAYMOB_HMAC_NOT_CONFIGURED", "PayMob HMAC secret is not configured");
        }
    }

    private void verifyCallbackHmac(PayMobTransactionCallbackRequest request) {
        if (request.getHmac() == null || request.getHmac().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYMOB_CALLBACK_HMAC_MISSING", "PayMob callback HMAC is missing");
        }

        String expectedHmac = buildCallbackHmac(request);
        if (!expectedHmac.equalsIgnoreCase(request.getHmac().trim())) {
            log.warn(
                    "Rejected PayMob callback because HMAC verification failed for providerEventId={} providerOrderId={}",
                    getProviderEventId(request),
                    getExternalOrderId(request)
            );
            throw new ApiException(HttpStatus.UNAUTHORIZED, "PAYMOB_CALLBACK_HMAC_INVALID", "PayMob callback HMAC verification failed");
        }
    }

    private void validateCallbackTransaction(PayMobTransactionCallbackRequest request) {
        String externalOrderId = getExternalOrderId(request);
        BillingPaymentAttemptValidationContext context = dbBillingWriteModels.findPaymentAttemptValidationContext("paymob", externalOrderId);
        if (context == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PAYMOB_ORDER_NOT_FOUND", "No payment attempt matches the callback order reference");
        }

        String expectedAmountCents = toAmountCents(context.getRequestedAmount());
        if (!expectedAmountCents.equals(String.valueOf(request.getTransaction().getAmountCents()))) {
            log.warn(
                    "Rejected PayMob callback because amount mismatch was detected for providerOrderId={} expectedAmountCents={} callbackAmountCents={}",
                    externalOrderId,
                    expectedAmountCents,
                    request.getTransaction().getAmountCents()
            );
            throw new ApiException(HttpStatus.CONFLICT, "PAYMOB_AMOUNT_MISMATCH", "PayMob callback amount does not match the payment attempt");
        }

        String callbackCurrency = normalizePayMobCurrency(request.getTransaction().getCurrency());
        String expectedCurrency = normalizePayMobCurrency(context.getCurrencyCode());
        if (!callbackCurrency.isBlank() && !expectedCurrency.isBlank() && !callbackCurrency.equals(expectedCurrency)) {
            log.warn(
                    "Rejected PayMob callback because currency mismatch was detected for providerOrderId={} expectedCurrency={} callbackCurrency={}",
                    externalOrderId,
                    context.getCurrencyCode(),
                    request.getTransaction().getCurrency()
            );
            throw new ApiException(HttpStatus.CONFLICT, "PAYMOB_CURRENCY_MISMATCH", "PayMob callback currency does not match the payment attempt");
        }

        String providerEventId = getProviderEventId(request);
        if (context.getExternalPaymentReference() != null
                && !context.getExternalPaymentReference().isBlank()
                && !context.getExternalPaymentReference().equals(providerEventId)) {
            log.warn(
                    "Rejected PayMob callback because payment reference mismatch was detected for providerOrderId={} storedReference={} callbackReference={}",
                    externalOrderId,
                    context.getExternalPaymentReference(),
                    providerEventId
            );
            throw new ApiException(HttpStatus.CONFLICT, "PAYMOB_PAYMENT_REFERENCE_MISMATCH", "PayMob callback payment reference does not match the existing payment attempt");
        }
    }

    private String buildCallbackHmac(PayMobTransactionCallbackRequest request) {
        PayMobTransactionCallbackRequest.TransactionPayload transaction = request.getTransaction();
        PayMobTransactionCallbackRequest.SourceDataPayload sourceData = transaction.getSourceData();
        StringJoiner payload = new StringJoiner("");
        payload.add(normalizeHmacValue(transaction.getAmountCents()));
        payload.add(normalizeHmacValue(transaction.getCreatedAt()));
        payload.add(normalizeHmacValue(transaction.getCurrency()));
        payload.add(normalizeHmacValue(transaction.getErrorOccured()));
        payload.add(normalizeHmacValue(transaction.getHasParentTransaction()));
        payload.add(normalizeHmacValue(transaction.getId()));
        payload.add(normalizeHmacValue(transaction.getIntegrationId()));
        payload.add(normalizeHmacValue(transaction.getSecure3d()));
        payload.add(normalizeHmacValue(transaction.getAuth()));
        payload.add(normalizeHmacValue(transaction.getCapture()));
        payload.add(normalizeHmacValue(transaction.getRefunded()));
        payload.add(normalizeHmacValue(transaction.getStandalonePayment()));
        payload.add(normalizeHmacValue(transaction.getVoided()));
        payload.add(normalizeHmacValue(transaction.getOrder() == null ? null : transaction.getOrder().getId()));
        payload.add(normalizeHmacValue(transaction.getOwner()));
        payload.add(normalizeHmacValue(transaction.getPending()));
        payload.add(normalizeHmacValue(sourceData == null ? null : sourceData.getPan()));
        payload.add(normalizeHmacValue(sourceData == null ? null : sourceData.getSubType()));
        payload.add(normalizeHmacValue(sourceData == null ? null : sourceData.getType()));
        payload.add(normalizeHmacValue(transaction.getSuccess()));

        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(payMobProperties.getHmacSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] digest = mac.doFinal(payload.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                builder.append(String.format(Locale.ROOT, "%02x", item));
            }
            return builder.toString();
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "PAYMOB_HMAC_COMPUTE_FAILED", "Failed to compute PayMob callback HMAC");
        }
    }

    private String normalizeHmacValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String safeLower(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizePayMobCurrency(String value) {
        String normalized = safeLower(value);
        if (normalized.isBlank()) {
            return "";
        }
        if ("le".equals(normalized) || "egp".equals(normalized)) {
            return "egp";
        }
        return normalized;
    }

    private String buildPayMobUrl(String path) {
        String baseUrl = payMobProperties.getBaseUrl().trim();
        String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return normalizedBaseUrl + normalizedPath;
    }

    private int extractInt(String body, String fieldName, String errorCode) {
        try {
            JsonNode jsonNode = objectMapper.readTree(body);
            JsonNode field = jsonNode.get(fieldName);
            if (field == null) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, errorCode, "Missing field " + fieldName + " in PayMob response");
            }
            return field.asInt();
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, errorCode, "Invalid PayMob response");
        }
    }

    private String extractString(String body, String fieldName, String errorCode) {
        try {
            JsonNode jsonNode = objectMapper.readTree(body);
            JsonNode field = jsonNode.get(fieldName);
            if (field == null || field.asText().isBlank()) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, errorCode, "Missing field " + fieldName + " in PayMob response");
            }
            return field.asText();
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, errorCode, "Invalid PayMob response");
        }
    }

    private HttpHeaders buildJsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        return headers;
    }

    private <T> ResponseEntity<String> executeJsonPost(String url,
                                                       T payload,
                                                       String errorCode,
                                                       String failureMessage) {
        try {
            HttpEntity<T> entity = new HttpEntity<>(payload, buildJsonHeaders());
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            if (response.getStatusCode() != HttpStatus.CREATED || response.getBody() == null) {
                log.error("PayMob request failed with status={} bodyPresent={}", response.getStatusCode(), response.getBody() != null);
            }
            return response;
        } catch (HttpStatusCodeException exception) {
            log.error(
                    "PayMob HTTP request failed for url={} status={} body={}",
                    url,
                    exception.getRawStatusCode(),
                    sanitizePayMobErrorBody(exception.getResponseBodyAsString())
            );
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    errorCode,
                    failureMessage,
                    List.of("PayMob status " + exception.getRawStatusCode() + ": " + sanitizePayMobErrorBody(exception.getResponseBodyAsString()))
            );
        } catch (RestClientException exception) {
            log.error("PayMob HTTP request failed for url={}", url, exception);
            throw new ApiException(HttpStatus.BAD_GATEWAY, errorCode, failureMessage);
        }
    }

    private String sanitizePayMobErrorBody(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "empty response";
        }

        return responseBody
                .replaceAll("(?i)\"token\"\\s*:\\s*\"[^\"]+\"", "\"token\":\"***\"")
                .replaceAll("(?i)\"auth_token\"\\s*:\\s*\"[^\"]+\"", "\"auth_token\":\"***\"")
                .replaceAll("(?i)\"api_key\"\\s*:\\s*\"[^\"]+\"", "\"api_key\":\"***\"");
    }

}
