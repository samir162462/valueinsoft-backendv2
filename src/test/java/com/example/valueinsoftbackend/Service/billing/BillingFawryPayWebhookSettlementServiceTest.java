package com.example.valueinsoftbackend.Service.billing;

import com.example.valueinsoftbackend.DatabaseRequests.DbBillingWriteModels;
import com.example.valueinsoftbackend.OnlinePayment.FawryPayProperties;
import com.example.valueinsoftbackend.Service.payment.PaymentAttemptService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class BillingFawryPayWebhookSettlementServiceTest {

    @Test
    void responseSignatureUsesFawryDocumentedFieldOrder() {
        BillingFawryPayWebhookSettlementService service = new BillingFawryPayWebhookSettlementService(
                mock(DbBillingWriteModels.class),
                properties(),
                mock(PaymentAttemptService.class),
                mock(BillingEntitlementService.class),
                new ObjectMapper()
        );
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("referenceNumber", "963455678");
        payload.put("paymentAmount", "749");
        payload.put("orderAmount", "749.0");
        payload.put("orderStatus", "PAID");
        payload.put("paymentMethod", "CARD");
        payload.put("fawryFees", "1");

        String signature = service.buildResponseSignature(payload, "100000001");

        assertEquals(sha256("963455678100000001749.00749.00PAIDCARD1.00HASH"), signature);
    }

    private FawryPayProperties properties() {
        FawryPayProperties properties = new FawryPayProperties();
        properties.setSecureHashKey("HASH");
        return properties;
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
            throw new IllegalStateException(exception);
        }
    }
}
