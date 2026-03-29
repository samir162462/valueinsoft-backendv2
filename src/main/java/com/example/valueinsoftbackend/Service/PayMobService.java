package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Request.PaymentTokenRequest;
import com.example.valueinsoftbackend.OnlinePayment.OPModel.Billing_data;
import com.example.valueinsoftbackend.OnlinePayment.OPModel.PaymentKeyRequest;
import com.example.valueinsoftbackend.OnlinePayment.OPModel.TransactionProcessedCallback;
import com.example.valueinsoftbackend.OnlinePayment.PayMobProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Service
public class PayMobService {

    private final PayMobProperties payMobProperties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    public PayMobService(PayMobProperties payMobProperties, ObjectMapper objectMapper) {
        this.payMobProperties = payMobProperties;
        this.objectMapper = objectMapper;
    }

    public String createAuthToken() {
        if (payMobProperties.getAuthToken() == null || payMobProperties.getAuthToken().isBlank()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "PAYMOB_NOT_CONFIGURED", "PayMob auth token is not configured");
        }

        String url = "https://accept.paymob.com/api/auth/tokens";
        Map<String, Object> payload = new HashMap<>();
        payload.put("api_key", payMobProperties.getAuthToken());
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, buildJsonHeaders());
        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

        if (response.getStatusCode() == HttpStatus.CREATED && response.getBody() != null) {
            return extractString(response.getBody(), "token", "PAYMOB_AUTH_FAILED");
        }

        throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYMOB_AUTH_FAILED", "PayMob authentication request failed");
    }

    public int createPayMobOrder(int merchantOrderId, int branchId, BigDecimal amountToPay) {
        String url = "https://accept.paymob.com/api/ecommerce/orders";
        Map<String, Object> payload = new HashMap<>();
        payload.put("auth_token", createAuthToken());
        payload.put("delivery_needed", "false");
        payload.put("amount_cents", toAmountCents(amountToPay));
        payload.put("currency", "EGP");
        payload.put("merchant_order_id", merchantOrderId);
        payload.put("items", Collections.emptyList());

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, buildJsonHeaders());
        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

        if (response.getStatusCode() == HttpStatus.CREATED && response.getBody() != null) {
            return extractInt(response.getBody(), "id", "PAYMOB_ORDER_REGISTRATION_FAILED");
        }

        throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYMOB_ORDER_REGISTRATION_FAILED", "PayMob order registration failed for branch " + branchId);
    }

    public String createPaymentKeyUrl(PaymentTokenRequest request) {
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

        String url = "https://accept.paymob.com/api/acceptance/payment_keys";
        HttpEntity<PaymentKeyRequest> entity = new HttpEntity<>(paymentKeyRequest, buildJsonHeaders());
        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

        if (response.getStatusCode() == HttpStatus.CREATED && response.getBody() != null) {
            String requestId = extractString(response.getBody(), "token", "PAYMOB_PAYMENT_KEY_FAILED");
            return "https://accept.paymob.com/api/acceptance/iframes/" + payMobProperties.getCardIFrameId() +
                    "?payment_token=" + requestId;
        }

        throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYMOB_PAYMENT_KEY_FAILED", "PayMob payment key request failed");
    }

    public TransactionProcessedCallback parseCallback(String body) {
        try {
            JsonNode jsonNode = objectMapper.readTree(body);
            JsonNode transactionNode = jsonNode.get("obj");
            if (transactionNode == null || transactionNode.get("order") == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "PAYMOB_CALLBACK_INVALID", "Invalid PayMob callback payload");
            }

            return new TransactionProcessedCallback(
                    transactionNode.get("id").asInt(),
                    transactionNode.get("pending").asBoolean(),
                    transactionNode.get("amount_cents").asInt(),
                    transactionNode.get("success").asBoolean(),
                    transactionNode.get("is_auth").asBoolean(),
                    transactionNode.get("is_capture").asBoolean(),
                    transactionNode.get("is_standalone_payment").asBoolean(),
                    transactionNode.get("is_voided").asBoolean(),
                    transactionNode.get("is_refunded").asBoolean(),
                    transactionNode.get("order").get("id").asInt()
            );
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYMOB_CALLBACK_INVALID", "Invalid PayMob callback payload");
        }
    }

    private String toAmountCents(BigDecimal amount) {
        return amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toPlainString();
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
}
