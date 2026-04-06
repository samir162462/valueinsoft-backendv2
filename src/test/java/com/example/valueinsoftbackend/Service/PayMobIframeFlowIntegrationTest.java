package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbBillingWriteModels;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Billing.BillingPaymentAttemptValidationContext;
import com.example.valueinsoftbackend.Model.Request.PaymentTokenRequest;
import com.example.valueinsoftbackend.Model.Request.PayMobTransactionCallbackRequest;
import com.example.valueinsoftbackend.OnlinePayment.OPModel.TransactionProcessedCallback;
import com.example.valueinsoftbackend.OnlinePayment.PayMobProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.StringJoiner;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("paymob-integration")
@SpringBootTest(
        classes = PayMobIframeFlowIntegrationTest.TestConfig.class,
        properties = {
                "logging.level.org.springframework.web.client.RestTemplate=INFO",
                "logging.level.org.springframework.web.HttpLogging=INFO"
        }
)
class PayMobIframeFlowIntegrationTest {

    @EnableConfigurationProperties(PayMobProperties.class)
    static class TestConfig {
    }

    @Autowired
    private PayMobProperties payMobProperties;

    @Test
    void iframeAcceptFlowCreatesProviderOrderPaymentKeyUrlAndValidatesCallback() throws Exception {
        assertConfigured(payMobProperties.getAuthToken(), "vls.paymob.auth-token");
        assertConfigured(payMobProperties.getHmacSecret(), "vls.paymob.hmac-secret");
        assertTrue(payMobProperties.getCardIntegrationId() > 0, "vls.paymob.card-integration-id must be configured");
        assertTrue(payMobProperties.getCardIFrameId() > 0, "vls.paymob.card-iframe-id must be configured");

        DbBillingWriteModels dbBillingWriteModels = mock(DbBillingWriteModels.class);
        PayMobService payMobService = new PayMobService(payMobProperties, dbBillingWriteModels, new ObjectMapper());

        int branchId = 1074;
        int companyId = 1095;
        BigDecimal amount = new BigDecimal("500.00");
        int merchantOrderId = ThreadLocalRandom.current().nextInt(10_740_000, 99_999_999);

        int providerOrderId = payMobService.createPayMobOrder(merchantOrderId, branchId, amount);
        assertTrue(providerOrderId > 0, "PayMob provider order id must be positive");

        PaymentTokenRequest paymentTokenRequest = new PaymentTokenRequest();
        paymentTokenRequest.setAmountCents(50000L);
        paymentTokenRequest.setOrderId(providerOrderId);
        paymentTokenRequest.setCurrency("EGP");
        paymentTokenRequest.setCompanyId(companyId);
        paymentTokenRequest.setBranchId(branchId);

        String checkoutUrl = payMobService.createPaymentKeyUrl(paymentTokenRequest);
        assertNotNull(checkoutUrl);
        assertTrue(checkoutUrl.startsWith(buildExpectedIframeUrlPrefix()),
                "PayMob checkout URL must use the configured iframe id");
        assertTrue(checkoutUrl.contains("payment_token="), "PayMob checkout URL must include a payment token");

        PayMobTransactionCallbackRequest callbackRequest = buildValidCallbackRequest(providerOrderId);
        callbackRequest.setHmac(computeHmac(callbackRequest, payMobProperties.getHmacSecret()));

        when(dbBillingWriteModels.findPaymentAttemptValidationContext(eq("paymob"), eq(String.valueOf(providerOrderId))))
                .thenReturn(new BillingPaymentAttemptValidationContext(
                        7L,
                        8L,
                        amount,
                        "EGP",
                        "checkout_requested",
                        null
                ));

        TransactionProcessedCallback callback = payMobService.parseCallback(callbackRequest);

        assertEquals(providerOrderId, callback.getSubId());
        assertEquals(50000, callback.getAmount_cents());
        assertTrue(callback.isSuccess());
    }

    private void assertConfigured(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new ApiException(
                    org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "PAYMOB_TEST_NOT_CONFIGURED",
                    propertyName + " must be configured for PayMob iframe integration test"
            );
        }
    }

    private PayMobTransactionCallbackRequest buildValidCallbackRequest(int providerOrderId) {
        PayMobTransactionCallbackRequest request = new PayMobTransactionCallbackRequest();
        request.setType("TRANSACTION");

        PayMobTransactionCallbackRequest.OrderPayload order = new PayMobTransactionCallbackRequest.OrderPayload();
        order.setId(providerOrderId);

        PayMobTransactionCallbackRequest.SourceDataPayload sourceData = new PayMobTransactionCallbackRequest.SourceDataPayload();
        sourceData.setPan("512345******2346");
        sourceData.setSubType("MasterCard");
        sourceData.setType("card");

        PayMobTransactionCallbackRequest.TransactionPayload transaction = new PayMobTransactionCallbackRequest.TransactionPayload();
        transaction.setId(987654);
        transaction.setPending(false);
        transaction.setAmountCents(50000);
        transaction.setCreatedAt("2026-04-06T12:00:00.000000");
        transaction.setCurrency("EGP");
        transaction.setErrorOccured(false);
        transaction.setHasParentTransaction(false);
        transaction.setIntegrationId(payMobProperties.getCardIntegrationId());
        transaction.setSecure3d(false);
        transaction.setSuccess(true);
        transaction.setAuth(false);
        transaction.setCapture(false);
        transaction.setStandalonePayment(true);
        transaction.setVoided(false);
        transaction.setRefunded(false);
        transaction.setOwner(1234);
        transaction.setOrder(order);
        transaction.setSourceData(sourceData);

        request.setTransaction(transaction);
        return request;
    }

    private String computeHmac(PayMobTransactionCallbackRequest request, String hmacSecret) throws Exception {
        PayMobTransactionCallbackRequest.TransactionPayload transaction = request.getTransaction();
        PayMobTransactionCallbackRequest.SourceDataPayload sourceData = transaction.getSourceData();
        StringJoiner payload = new StringJoiner("");
        payload.add(normalize(transaction.getAmountCents()));
        payload.add(normalize(transaction.getCreatedAt()));
        payload.add(normalize(transaction.getCurrency()));
        payload.add(normalize(transaction.getErrorOccured()));
        payload.add(normalize(transaction.getHasParentTransaction()));
        payload.add(normalize(transaction.getId()));
        payload.add(normalize(transaction.getIntegrationId()));
        payload.add(normalize(transaction.getSecure3d()));
        payload.add(normalize(transaction.getAuth()));
        payload.add(normalize(transaction.getCapture()));
        payload.add(normalize(transaction.getRefunded()));
        payload.add(normalize(transaction.getStandalonePayment()));
        payload.add(normalize(transaction.getVoided()));
        payload.add(normalize(transaction.getOrder().getId()));
        payload.add(normalize(transaction.getOwner()));
        payload.add(normalize(transaction.getPending()));
        payload.add(normalize(sourceData.getPan()));
        payload.add(normalize(sourceData.getSubType()));
        payload.add(normalize(sourceData.getType()));
        payload.add(normalize(transaction.getSuccess()));

        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        byte[] digest = mac.doFinal(payload.toString().getBytes(StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder(digest.length * 2);
        for (byte item : digest) {
            builder.append(String.format(Locale.ROOT, "%02x", item));
        }
        return builder.toString();
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String buildExpectedIframeUrlPrefix() {
        String baseUrl = payMobProperties.getBaseUrl().trim();
        String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return normalizedBaseUrl + "/api/acceptance/iframes/" + payMobProperties.getCardIFrameId();
    }
}
