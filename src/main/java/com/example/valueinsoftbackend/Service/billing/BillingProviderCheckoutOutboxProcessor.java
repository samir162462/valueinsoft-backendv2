package com.example.valueinsoftbackend.Service.billing;

import com.example.valueinsoftbackend.Config.BillingProperties;
import com.example.valueinsoftbackend.DatabaseRequests.DbBillingWriteModels;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Billing.BillingProviderCheckoutOutboxItem;
import com.example.valueinsoftbackend.Model.Request.PaymentTokenRequest;
import com.example.valueinsoftbackend.Service.payment.PaymentProvider;
import com.example.valueinsoftbackend.Service.payment.PaymentProviderResolver;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class BillingProviderCheckoutOutboxProcessor {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100L);

    private final DbBillingWriteModels dbBillingWriteModels;
    private final PaymentProviderResolver paymentProviderResolver;
    private final BillingProperties billingProperties;
    private final ObjectMapper objectMapper;

    public BillingProviderCheckoutOutboxProcessor(DbBillingWriteModels dbBillingWriteModels,
                                                  PaymentProviderResolver paymentProviderResolver,
                                                  BillingProperties billingProperties,
                                                  ObjectMapper objectMapper) {
        this.dbBillingWriteModels = dbBillingWriteModels;
        this.paymentProviderResolver = paymentProviderResolver;
        this.billingProperties = billingProperties;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${vls.billing.checkout-outbox-worker-delay-ms:30000}")
    public void runScheduledCheckoutOutbox() {
        if (!billingProperties.isCheckoutOutboxWorkerEnabled()) {
            return;
        }
        processDueCheckoutRequests();
    }

    public int processDueCheckoutRequests() {
        int batchSize = Math.max(1, billingProperties.getCheckoutOutboxBatchSize());
        List<BillingProviderCheckoutOutboxItem> items = dbBillingWriteModels.claimDueProviderCheckoutOutboxItems(batchSize);
        for (BillingProviderCheckoutOutboxItem item : items) {
            processItem(item);
        }
        return items.size();
    }

    private void processItem(BillingProviderCheckoutOutboxItem item) {
        String externalOrderId = null;
        try {
            if (item.getBranchId() == null) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "BILLING_CHECKOUT_BRANCH_REQUIRED",
                        "Provider checkout requires a branch id"
                );
            }

            PaymentProvider provider = paymentProviderResolver.getProvider(item.getProviderCode());
            int merchantOrderId = stableMerchantOrderId(item);
            int providerOrderId = provider.createProviderOrder(merchantOrderId, item.getBranchId(), amount(item.getRequestedAmount()));
            externalOrderId = String.valueOf(providerOrderId);

            PaymentTokenRequest tokenRequest = new PaymentTokenRequest();
            tokenRequest.setOrderId(providerOrderId);
            tokenRequest.setBranchId(item.getBranchId());
            tokenRequest.setCompanyId(item.getCompanyId());
            tokenRequest.setCurrency(normalizeCurrency(item.getCurrencyCode()));
            tokenRequest.setAmountCents(toAmountCents(item.getRequestedAmount()));
            String checkoutUrl = provider.createPaymentKeyUrl(tokenRequest);

            String providerResponseJson = toJson(Map.of(
                    "source", "billing_provider_checkout_outbox",
                    "checkoutUrl", checkoutUrl,
                    "externalOrderId", externalOrderId,
                    "checkoutReference", valueOrEmpty(item.getCheckoutReference())
            ));
            int updated = dbBillingWriteModels.updatePaymentAttemptCheckoutRequestedById(
                    item.getBillingPaymentAttemptId(),
                    externalOrderId,
                    item.getCheckoutReference(),
                    providerResponseJson,
                    "{\"source\":\"billing_provider_checkout_outbox\"}"
            );
            if (updated == 0) {
                dbBillingWriteModels.markProviderCheckoutOutboxFinal(
                        item.getCheckoutOutboxId(),
                        "FAILED_FINAL",
                        providerResponseJson,
                        "Payment attempt was terminal or missing before checkout response could be persisted"
                );
                return;
            }
            dbBillingWriteModels.markProviderCheckoutOutboxSucceeded(item.getCheckoutOutboxId(), providerResponseJson);
        } catch (ApiException exception) {
            handleFailure(item, externalOrderId, exception.getCode(), exception.getMessage(), isFinal(exception));
        } catch (RuntimeException exception) {
            handleFailure(item, externalOrderId, "PROVIDER_CHECKOUT_ERROR", exception.getMessage(), false);
        }
    }

    private void handleFailure(BillingProviderCheckoutOutboxItem item,
                               String externalOrderId,
                               String failureCode,
                               String failureMessage,
                               boolean finalFailure) {
        String safeMessage = failureMessage == null || failureMessage.isBlank()
                ? "Provider checkout failed"
                : failureMessage;
        String responsePayloadJson = toJson(Map.of(
                "source", "billing_provider_checkout_outbox",
                "externalOrderId", valueOrEmpty(externalOrderId),
                "failureCode", valueOrEmpty(failureCode),
                "failureMessage", safeMessage
        ));
        if (externalOrderId != null && !externalOrderId.isBlank()) {
            dbBillingWriteModels.updatePaymentAttemptPendingProviderById(
                    item.getBillingPaymentAttemptId(),
                    externalOrderId,
                    responsePayloadJson,
                    "PROVIDER_CHECKOUT_UNCERTAIN",
                    safeMessage
            );
            dbBillingWriteModels.markProviderCheckoutOutboxFinal(
                    item.getCheckoutOutboxId(),
                    "FAILED_UNCERTAIN",
                    responsePayloadJson,
                    safeMessage
            );
            return;
        }

        if (finalFailure || item.getAttemptCount() >= Math.max(1, billingProperties.getCheckoutOutboxMaxAttempts())) {
            dbBillingWriteModels.markProviderCheckoutOutboxFinal(
                    item.getCheckoutOutboxId(),
                    "FAILED_FINAL",
                    responsePayloadJson,
                    safeMessage
            );
            return;
        }

        dbBillingWriteModels.markProviderCheckoutOutboxRetryable(
                item.getCheckoutOutboxId(),
                safeMessage,
                Math.max(1, billingProperties.getCheckoutOutboxRetryDelaySeconds())
        );
    }

    private boolean isFinal(ApiException exception) {
        return exception.getStatus() == HttpStatus.NOT_IMPLEMENTED || exception.getStatus() == HttpStatus.BAD_REQUEST;
    }

    private int stableMerchantOrderId(BillingProviderCheckoutOutboxItem item) {
        long stableValue = item.getBillingPaymentAttemptId() % 1_000_000_000L;
        return (int) (stableValue + 100_000_000L);
    }

    private long toAmountCents(BigDecimal amount) {
        return amount(amount)
                .multiply(ONE_HUNDRED)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    private BigDecimal amount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : value.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private String normalizeCurrency(String currencyCode) {
        return currencyCode == null || currencyCode.isBlank() ? "EGP" : currencyCode.trim().toUpperCase();
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private String toJson(Map<String, ?> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            log.warn("Failed to serialize billing checkout outbox payload", exception);
            return "{}";
        }
    }
}
