package com.example.valueinsoftbackend.OnlinePayment.OPController;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Request.PaymentTokenRequest;
import com.example.valueinsoftbackend.Model.Request.PayMobTransactionCallbackRequest;
import com.example.valueinsoftbackend.OnlinePayment.OPModel.TransactionProcessedCallback;
import com.example.valueinsoftbackend.Service.BillingProviderEventService;
import com.example.valueinsoftbackend.Service.PaymentProviderFinanceIntegrationService;
import com.example.valueinsoftbackend.Service.PaymentProvider;
import com.example.valueinsoftbackend.Service.PaymentAttemptService;
import com.example.valueinsoftbackend.Service.PaymentProviderResolver;
import com.example.valueinsoftbackend.Service.PayMobService;
import com.example.valueinsoftbackend.Service.SubscriptionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@Validated
@RequestMapping("/OP")
@Slf4j
public class PayMobController {

    private final PaymentProviderResolver paymentProviderResolver;
    private final PaymentAttemptService paymentAttemptService;
    private final SubscriptionService subscriptionService;
    private final BillingProviderEventService billingProviderEventService;
    private final PaymentProviderFinanceIntegrationService paymentProviderFinanceIntegrationService;
    private final PayMobService payMobService;
    private final ObjectMapper objectMapper;

    public PayMobController(PaymentProviderResolver paymentProviderResolver,
                            PaymentAttemptService paymentAttemptService,
                            SubscriptionService subscriptionService,
                            BillingProviderEventService billingProviderEventService,
                            PaymentProviderFinanceIntegrationService paymentProviderFinanceIntegrationService,
                            PayMobService payMobService,
                            ObjectMapper objectMapper) {
        this.paymentProviderResolver = paymentProviderResolver;
        this.paymentAttemptService = paymentAttemptService;
        this.subscriptionService = subscriptionService;
        this.billingProviderEventService = billingProviderEventService;
        this.paymentProviderFinanceIntegrationService = paymentProviderFinanceIntegrationService;
        this.payMobService = payMobService;
        this.objectMapper = objectMapper;
    }

    @RequestMapping(value = "/paymentTKNRequest", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<String> paymentKeyRequest(@RequestBody PaymentTokenRequest body) {
        PaymentProvider paymentProvider = paymentProviderResolver.getActiveProvider();
        log.info(
                "Starting payment checkout for provider={} providerOrderId={} branchId={} companyId={} amountCents={} currency={}",
                paymentProvider.getProviderCode(),
                body.getOrderId(),
                body.getBranchId(),
                body.getCompanyId(),
                body.getAmountCents(),
                body.getCurrency()
        );
        String checkoutUrl = paymentProvider.createPaymentKeyUrl(body);
        paymentAttemptService.markCheckoutRequested(
                paymentProvider.getProviderCode(),
                String.valueOf(body.getOrderId()),
                "{\"checkoutUrl\":\"" + checkoutUrl + "\"}"
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(checkoutUrl);
    }

    @GetMapping("/TPC")
    public ResponseEntity<Map<String, Object>> transactionProcessedCallbackHealth() {
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "endpoint", "/OP/TPC",
                "expectedMethod", "POST",
                "purpose", "PayMob transaction processed callback"
        ));
    }

    @RequestMapping(value = "/TPC", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<TransactionProcessedCallback> transactionProcessedCallbackResp(
            @RequestBody(required = false) PayMobTransactionCallbackRequest body,
            @RequestParam(required = false) Map<String, String> params
    ) {
        PayMobTransactionCallbackRequest callbackRequest = normalizeCallbackRequest(body, params);
        PaymentProvider paymentProvider = paymentProviderResolver.getActiveProvider();
        String providerCode = paymentProvider.getProviderCode();
        String providerEventId = payMobService.getProviderEventId(callbackRequest);
        String externalOrderId = payMobService.getExternalOrderId(callbackRequest);
        String payloadJson = toJson(callbackRequest);

        log.info(
                "Received payment callback provider={} providerEventId={} providerOrderId={}",
                providerCode,
                providerEventId,
                externalOrderId
        );

        try {
            TransactionProcessedCallback callback = paymentProvider.parseTransactionCallback(callbackRequest);

            if (providerEventId != null && billingProviderEventService.isProcessedEvent(providerCode, providerEventId)) {
                log.info(
                        "Ignoring duplicate processed payment callback provider={} providerEventId={} providerOrderId={}",
                        providerCode,
                        providerEventId,
                        externalOrderId
                );
                return ResponseEntity.ok(callback);
            }

            if (callback.isSuccess()) {
                subscriptionService.markBranchSubscriptionStatusSuccess(callback.getSubId());
                paymentProviderFinanceIntegrationService.enqueuePayMobSettlement(
                        providerCode,
                        externalOrderId,
                        providerEventId,
                        callbackRequest);
            } else {
                paymentAttemptService.markFailed(
                        providerCode,
                        externalOrderId,
                        payloadJson,
                        "PAYMOB_CALLBACK_REPORTED_FAILURE",
                        "Provider callback marked the transaction as not successful",
                        providerEventId
                );
            }

            if (providerEventId != null) {
                billingProviderEventService.recordProcessedEvent(
                        providerCode,
                        providerEventId,
                        "transaction_callback",
                        externalOrderId,
                        payloadJson
                );
            }

            log.info(
                    "Finished payment callback provider={} providerEventId={} providerOrderId={} success={}",
                    providerCode,
                    providerEventId,
                    externalOrderId,
                    callback.isSuccess()
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(callback);
        } catch (RuntimeException exception) {
            if (providerEventId != null) {
                billingProviderEventService.recordFailedEvent(
                        providerCode,
                        providerEventId,
                        "transaction_callback",
                        externalOrderId,
                        payloadJson,
                        exception.getMessage()
                );
            }
            log.warn(
                    "Payment callback rejected provider={} providerEventId={} providerOrderId={} message={}",
                    providerCode,
                    providerEventId,
                    externalOrderId,
                    exception.getMessage()
            );
            throw exception;
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
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
