package com.example.valueinsoftbackend.OnlinePayment.OPController;

import com.example.valueinsoftbackend.DatabaseRequests.DbApp.DbSubscription;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.OnlinePayment.OPModel.Billing_data;
import com.example.valueinsoftbackend.OnlinePayment.OPModel.PaymentKeyRequest;
import com.example.valueinsoftbackend.OnlinePayment.OPModel.TransactionProcessedCallback;
import com.example.valueinsoftbackend.OnlinePayment.PayMobProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/OP")
public class PayMobController {

    private static PayMobProperties configuredPayMobProperties;
    private final PayMobProperties payMobProperties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();
    private String pMNToken;

    public PayMobController(PayMobProperties payMobProperties, ObjectMapper objectMapper) {
        this.payMobProperties = payMobProperties;
        this.objectMapper = objectMapper;
        configuredPayMobProperties = payMobProperties;
    }

    public static String createPostAuth() {
        if (configuredPayMobProperties == null || configuredPayMobProperties.getAuthToken() == null
                || configuredPayMobProperties.getAuthToken().isBlank()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "PAYMOB_NOT_CONFIGURED", "PayMob auth token is not configured");
        }

        String url = "https://accept.paymob.com/api/auth/tokens";
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = buildJsonHeaders();
        Map<String, Object> map = new HashMap<>();
        map.put("api_key", configuredPayMobProperties.getAuthToken());
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(map, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

        if (response.getStatusCode() == HttpStatus.CREATED && response.getBody() != null) {
            return response.getBody().split(":")[1].split(",")[0].replace("\"", "");
        }

        throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYMOB_AUTH_FAILED", "PayMob authentication request failed");
    }

    private String getPaymentKeyRequestToken(PaymentKeyRequest paymentKeyRequest) {
        String url = "https://accept.paymob.com/api/acceptance/payment_keys";
        HttpEntity<PaymentKeyRequest> entity = new HttpEntity<>(paymentKeyRequest, buildJsonHeaders());
        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

        if (response.getStatusCode() == HttpStatus.CREATED && response.getBody() != null) {
            return response.getBody().split(":")[1].split(",")[0].replace("\"", "").replace("}", "");
        }

        throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYMOB_PAYMENT_KEY_FAILED", "PayMob payment key request failed");
    }

    @RequestMapping(value = "/paymentTKNRequest", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<String> paymentKeyRequest(@RequestBody Map<String, String> body) {
        pMNToken = createPostAuth();
        String amount = body.get("amount_cents");
        String orderId = body.get("order_id");
        String currency = body.get("currency");
        String companyId = body.get("companyId");
        String branchId = body.get("branchId");
        Billing_data billingData = new Billing_data(branchId, companyId, branchId + companyId + "@VLS.com", branchId + companyId);
        PaymentKeyRequest paymentKeyRequest = new PaymentKeyRequest(
                pMNToken,
                amount,
                3600,
                orderId,
                billingData,
                currency,
                payMobProperties.getCardIntegrationId(),
                "false"
        );

        String requestId = getPaymentKeyRequestToken(paymentKeyRequest);
        String url = "https://accept.paymob.com/api/acceptance/iframes/" + payMobProperties.getCardIFrameId() +
                "?payment_token=" + requestId;
        return ResponseEntity.status(HttpStatus.CREATED).body(url);
    }

    @RequestMapping(value = "/TPC", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<TransactionProcessedCallback> transactionProcessedCallbackResp(@RequestBody String body) {
        try {
            JsonNode jsonNode = objectMapper.readTree(body);
            TransactionProcessedCallback callback = new TransactionProcessedCallback(
                    jsonNode.get("obj").get("id").asInt(),
                    jsonNode.get("obj").get("pending").asBoolean(),
                    jsonNode.get("obj").get("amount_cents").asInt(),
                    jsonNode.get("obj").get("success").asBoolean(),
                    jsonNode.get("obj").get("is_auth").asBoolean(),
                    jsonNode.get("obj").get("is_capture").asBoolean(),
                    jsonNode.get("obj").get("is_standalone_payment").asBoolean(),
                    jsonNode.get("obj").get("is_voided").asBoolean(),
                    jsonNode.get("obj").get("is_refunded").asBoolean(),
                    jsonNode.get("obj").get("order").get("id").asInt()
            );
            if (callback.isSuccess()) {
                DbSubscription.updateBranchSubscriptionStatusSuccess(callback.getSubId(), true);
            }
            return ResponseEntity.status(HttpStatus.CREATED).body(callback);
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYMOB_CALLBACK_INVALID", "Invalid PayMob callback payload");
        }
    }

    private static HttpHeaders buildJsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        return headers;
    }
}
