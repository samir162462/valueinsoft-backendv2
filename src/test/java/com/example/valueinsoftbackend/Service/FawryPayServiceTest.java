package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Request.PaymentTokenRequest;
import com.example.valueinsoftbackend.OnlinePayment.FawryPayProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestOperations;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FawryPayServiceTest {

    @Test
    void createFawryPayOrderUsesMerchantOrderIdAsProviderReference() {
        FawryPayService service = new FawryPayService(properties(), new ObjectMapper(), mock(RestOperations.class));

        assertEquals(100000123, service.createFawryPayOrder(100000123));
    }

    @Test
    void createCheckoutUrlPostsSignedHostedCheckoutRequest() {
        RestOperations restOperations = mock(RestOperations.class);
        when(restOperations.postForEntity(
                eq("https://atfawry.fawrystaging.com/ECommerceWeb/Fawry/payments/charge"),
                org.mockito.ArgumentMatchers.any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(ResponseEntity.ok("{\"redirectUrl\":\"https://fawry.test/checkout/abc\"}"));
        FawryPayService service = new FawryPayService(properties(), new ObjectMapper(), restOperations);

        String checkoutUrl = service.createCheckoutUrl(paymentTokenRequest());

        assertEquals("https://fawry.test/checkout/abc", checkoutUrl);
        ArgumentCaptor<HttpEntity<Map<String, Object>>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restOperations).postForEntity(
                eq("https://atfawry.fawrystaging.com/ECommerceWeb/Fawry/payments/charge"),
                captor.capture(),
                eq(String.class)
        );
        Map<String, Object> payload = captor.getValue().getBody();
        assertEquals("MERCHANT", payload.get("merchantCode"));
        assertEquals("100000001", payload.get("merchantRefNum"));
        assertEquals("10", payload.get("customerProfileId"));
        assertEquals("https://front.test/payment-response", payload.get("returnUrl"));
        assertEquals("CARD", payload.get("paymentMethod"));

        List<Map<String, Object>> chargeItems = (List<Map<String, Object>>) payload.get("chargeItems");
        assertEquals("billing-invoice-100000001", chargeItems.get(0).get("itemId"));
        assertEquals(1, chargeItems.get(0).get("quantity"));
        assertEquals(new BigDecimal("749.00"), chargeItems.get(0).get("price"));
        assertEquals(expectedSignature(), payload.get("signature"));
    }

    @Test
    void nonEgpCurrencyIsRejectedBeforeProviderRequest() {
        FawryPayService service = new FawryPayService(properties(), new ObjectMapper(), mock(RestOperations.class));
        PaymentTokenRequest request = paymentTokenRequest();
        request.setCurrency("USD");

        ApiException exception = assertThrows(ApiException.class, () -> service.createCheckoutUrl(request));

        assertEquals("FAWRYPAY_CURRENCY_NOT_SUPPORTED", exception.getCode());
    }

    private FawryPayProperties properties() {
        FawryPayProperties properties = new FawryPayProperties();
        properties.setBaseUrl("https://atfawry.fawrystaging.com");
        properties.setChargePath("/ECommerceWeb/Fawry/payments/charge");
        properties.setMerchantCode("MERCHANT");
        properties.setSecureHashKey("HASH");
        properties.setReturnUrl("https://front.test/payment-response");
        properties.setPaymentMethod("CARD");
        properties.setPaymentExpiryMinutes(60);
        return properties;
    }

    private PaymentTokenRequest paymentTokenRequest() {
        PaymentTokenRequest request = new PaymentTokenRequest();
        request.setOrderId(100000001L);
        request.setAmountCents(74900L);
        request.setCurrency("EGP");
        request.setCompanyId(10);
        request.setBranchId(20);
        return request;
    }

    private String expectedSignature() {
        return sha256("MERCHANT10000000110https://front.test/payment-responsebilling-invoice-1000000011749.00HASH");
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
