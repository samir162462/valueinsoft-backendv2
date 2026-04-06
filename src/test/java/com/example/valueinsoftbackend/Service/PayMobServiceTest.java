package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbBillingWriteModels;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Billing.BillingPaymentAttemptValidationContext;
import com.example.valueinsoftbackend.Model.Request.PayMobTransactionCallbackRequest;
import com.example.valueinsoftbackend.OnlinePayment.OPModel.TransactionProcessedCallback;
import com.example.valueinsoftbackend.OnlinePayment.PayMobProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.StringJoiner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class PayMobServiceTest {

    private static final String HMAC_SECRET = "test-paymob-hmac-secret";

    private DbBillingWriteModels dbBillingWriteModels;
    private PayMobService payMobService;

    @BeforeEach
    void setUp() {
        PayMobProperties payMobProperties = new PayMobProperties();
        payMobProperties.setAuthToken("test-auth-token");
        payMobProperties.setCardIntegrationId(1989683);
        payMobProperties.setCardIFrameId(370887);
        payMobProperties.setHmacSecret(HMAC_SECRET);

        dbBillingWriteModels = Mockito.mock(DbBillingWriteModels.class);
        payMobService = new PayMobService(payMobProperties, dbBillingWriteModels, new ObjectMapper());
    }

    @Test
    void parseCallbackAcceptsVerifiedMatchingCallback() throws Exception {
        PayMobTransactionCallbackRequest request = buildValidCallbackRequest();
        request.setHmac(computeHmac(request, HMAC_SECRET));

        when(dbBillingWriteModels.findPaymentAttemptValidationContext(eq("paymob"), eq("123456")))
                .thenReturn(new BillingPaymentAttemptValidationContext(
                        7L,
                        8L,
                        new BigDecimal("500.00"),
                        "EGP",
                        "checkout_requested",
                        null
                ));

        TransactionProcessedCallback callback = payMobService.parseCallback(request);

        assertEquals(987654, callback.getOrder_id());
        assertEquals(123456, callback.getSubId());
        assertEquals(50000, callback.getAmount_cents());
        assertEquals(true, callback.isSuccess());
    }

    @Test
    void parseCallbackRejectsInvalidHmac() {
        PayMobTransactionCallbackRequest request = buildValidCallbackRequest();
        request.setHmac("invalid-hmac");

        when(dbBillingWriteModels.findPaymentAttemptValidationContext(eq("paymob"), eq("123456")))
                .thenReturn(new BillingPaymentAttemptValidationContext(
                        7L,
                        8L,
                        new BigDecimal("500.00"),
                        "EGP",
                        "checkout_requested",
                        null
                ));

        ApiException exception = assertThrows(ApiException.class, () -> payMobService.parseCallback(request));

        assertEquals("PAYMOB_CALLBACK_HMAC_INVALID", exception.getCode());
    }

    @Test
    void parseCallbackRejectsAmountMismatch() throws Exception {
        PayMobTransactionCallbackRequest request = buildValidCallbackRequest();
        request.setHmac(computeHmac(request, HMAC_SECRET));

        when(dbBillingWriteModels.findPaymentAttemptValidationContext(eq("paymob"), eq("123456")))
                .thenReturn(new BillingPaymentAttemptValidationContext(
                        7L,
                        8L,
                        new BigDecimal("400.00"),
                        "EGP",
                        "checkout_requested",
                        null
                ));

        ApiException exception = assertThrows(ApiException.class, () -> payMobService.parseCallback(request));

        assertEquals("PAYMOB_AMOUNT_MISMATCH", exception.getCode());
    }

    private PayMobTransactionCallbackRequest buildValidCallbackRequest() {
        PayMobTransactionCallbackRequest request = new PayMobTransactionCallbackRequest();
        request.setType("TRANSACTION");

        PayMobTransactionCallbackRequest.OrderPayload order = new PayMobTransactionCallbackRequest.OrderPayload();
        order.setId(123456);

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
        transaction.setIntegrationId(1989683);
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
}
