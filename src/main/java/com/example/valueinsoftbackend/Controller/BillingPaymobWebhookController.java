package com.example.valueinsoftbackend.Controller;

import com.example.valueinsoftbackend.Model.Billing.BillingPaymobWebhookSettlementResponse;
import com.example.valueinsoftbackend.Model.Request.PayMobTransactionCallbackRequest;
import com.example.valueinsoftbackend.Service.billing.BillingPaymobWebhookSettlementService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@Validated
@RequestMapping("/api/billing/paymob")
public class BillingPaymobWebhookController {

    private final BillingPaymobWebhookSettlementService settlementService;

    public BillingPaymobWebhookController(BillingPaymobWebhookSettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @PostMapping("/webhook")
    public ResponseEntity<BillingPaymobWebhookSettlementResponse> settlePaymobWebhook(
            @RequestBody(required = false) PayMobTransactionCallbackRequest body,
            @RequestParam(required = false) Map<String, String> params) {
        return ResponseEntity.ok(settlementService.settleTransactionCallback(normalizeCallbackRequest(body, params)));
    }

    private PayMobTransactionCallbackRequest normalizeCallbackRequest(PayMobTransactionCallbackRequest body,
                                                                      Map<String, String> params) {
        if (body != null && body.getTransaction() != null && body.getHmac() != null && !body.getHmac().isBlank()) {
            return body;
        }

        if (params == null || params.isEmpty()) {
            return body == null ? new PayMobTransactionCallbackRequest() : body;
        }

        PayMobTransactionCallbackRequest request = body == null ? new PayMobTransactionCallbackRequest() : body;
        request.setHmac(firstNonBlank(request.getHmac(), params.get("hmac")));
        request.setType(firstNonBlank(request.getType(), params.get("type"), "TRANSACTION"));

        PayMobTransactionCallbackRequest.TransactionPayload transaction =
                request.getTransaction() == null ? new PayMobTransactionCallbackRequest.TransactionPayload() : request.getTransaction();
        transaction.setId(firstNonNull(transaction.getId(), parseInteger(params.get("id"))));
        transaction.setPending(firstNonNull(transaction.getPending(), parseBoolean(params.get("pending"))));
        transaction.setAmountCents(firstNonNull(transaction.getAmountCents(), parseInteger(params.get("amount_cents"))));
        transaction.setCreatedAt(firstNonBlank(transaction.getCreatedAt(), params.get("created_at")));
        transaction.setCurrency(firstNonBlank(transaction.getCurrency(), params.get("currency")));
        transaction.setErrorOccured(firstNonNull(transaction.getErrorOccured(), parseBoolean(params.get("error_occured"))));
        transaction.setHasParentTransaction(firstNonNull(transaction.getHasParentTransaction(), parseBoolean(params.get("has_parent_transaction"))));
        transaction.setIntegrationId(firstNonNull(transaction.getIntegrationId(), parseInteger(params.get("integration_id"))));
        transaction.setSecure3d(firstNonNull(transaction.getSecure3d(), parseBoolean(params.get("is_3d_secure"))));
        transaction.setSuccess(firstNonNull(transaction.getSuccess(), parseBoolean(params.get("success"))));
        transaction.setAuth(firstNonNull(transaction.getAuth(), parseBoolean(params.get("is_auth"))));
        transaction.setCapture(firstNonNull(transaction.getCapture(), parseBoolean(params.get("is_capture"))));
        transaction.setStandalonePayment(firstNonNull(transaction.getStandalonePayment(), parseBoolean(params.get("is_standalone_payment"))));
        transaction.setVoided(firstNonNull(transaction.getVoided(), parseBoolean(params.get("is_voided"), params.get("is_void"))));
        transaction.setRefunded(firstNonNull(transaction.getRefunded(), parseBoolean(params.get("is_refunded"), params.get("is_refund"))));
        transaction.setOwner(firstNonNull(transaction.getOwner(), parseInteger(params.get("owner"))));

        PayMobTransactionCallbackRequest.OrderPayload order =
                transaction.getOrder() == null ? new PayMobTransactionCallbackRequest.OrderPayload() : transaction.getOrder();
        order.setId(firstNonNull(order.getId(), parseInteger(params.get("order"))));
        transaction.setOrder(order);

        PayMobTransactionCallbackRequest.SourceDataPayload sourceData =
                transaction.getSourceData() == null ? new PayMobTransactionCallbackRequest.SourceDataPayload() : transaction.getSourceData();
        sourceData.setPan(firstNonBlank(sourceData.getPan(), params.get("source_data.pan")));
        sourceData.setSubType(firstNonBlank(sourceData.getSubType(), params.get("source_data.sub_type")));
        sourceData.setType(firstNonBlank(sourceData.getType(), params.get("source_data.type")));
        transaction.setSourceData(sourceData);

        request.setTransaction(transaction);
        return request;
    }

    @SafeVarargs
    private final <T> T firstNonNull(T currentValue, T... fallbackValues) {
        if (currentValue != null) {
            return currentValue;
        }
        if (fallbackValues == null) {
            return null;
        }
        for (T fallbackValue : fallbackValues) {
            if (fallbackValue != null) {
                return fallbackValue;
            }
        }
        return null;
    }

    private String firstNonBlank(String currentValue, String... fallbackValues) {
        if (currentValue != null && !currentValue.isBlank()) {
            return currentValue;
        }
        if (fallbackValues == null) {
            return currentValue;
        }
        for (String fallbackValue : fallbackValues) {
            if (fallbackValue != null && !fallbackValue.isBlank()) {
                return fallbackValue;
            }
        }
        return currentValue;
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Boolean parseBoolean(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return Boolean.parseBoolean(value.trim());
            }
        }
        return null;
    }
}
