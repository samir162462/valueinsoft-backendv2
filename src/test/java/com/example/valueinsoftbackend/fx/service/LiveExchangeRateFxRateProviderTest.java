package com.example.valueinsoftbackend.fx.service;

import com.example.valueinsoftbackend.Config.FxDeepSeekProperties;
import com.example.valueinsoftbackend.fx.model.FxDeepSeekFetchResult;
import com.example.valueinsoftbackend.fx.model.FxRatePayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiveExchangeRateFxRateProviderTest {

    @Test
    void fetchesConfiguredEndpointAndMapsTargetRate() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.getForEntity("https://open.er-api.com/v6/latest/USD", String.class))
                .thenReturn(ResponseEntity.ok(successResponse()));

        FxDeepSeekFetchResult result = new LiveExchangeRateFxRateProvider(
                new ObjectMapper(),
                properties(),
                restTemplate
        ).fetchRate();

        FxRatePayload payload = result.payload();
        assertEquals("USD", payload.baseCurrency());
        assertEquals("EGP", payload.targetCurrency());
        assertEquals(0, new BigDecimal("51.81532").compareTo(payload.rate()));
        assertEquals("MARKET", payload.rateType());
        assertEquals(LocalDate.of(2026, 6, 8), payload.effectiveDate());
        assertEquals(BigDecimal.ONE, payload.confidence());
        assertTrue(payload.sourceDescription().contains("https://www.exchangerate-api.com"));
        assertTrue(result.rawResponse().contains("\"EGP\": 51.81532"));

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(restTemplate).getForEntity(urlCaptor.capture(), org.mockito.ArgumentMatchers.eq(String.class));
        assertEquals("https://open.er-api.com/v6/latest/USD", urlCaptor.getValue());
    }

    @Test
    void rejectsResponseMissingTargetCurrency() {
        LiveExchangeRateFxRateProvider provider = new LiveExchangeRateFxRateProvider(
                new ObjectMapper(),
                properties(),
                mock(RestTemplate.class)
        );

        assertThrows(FxDeepSeekRateException.class, () -> provider.parsePayload("""
                {
                  "result": "success",
                  "base_code": "USD",
                  "time_last_update_utc": "Mon, 08 Jun 2026 00:02:31 +0000",
                  "rates": { "EUR": 0.86 }
                }
                """));
    }

    private FxDeepSeekProperties properties() {
        FxDeepSeekProperties properties = new FxDeepSeekProperties();
        properties.setEndpointUrl("https://open.er-api.com/v6/latest/{baseCurrency}");
        properties.setBaseCurrency("USD");
        properties.setTargetCurrency("EGP");
        return properties;
    }

    private String successResponse() {
        return """
                {
                  "result": "success",
                  "provider": "https://www.exchangerate-api.com",
                  "time_last_update_unix": 1780876951,
                  "time_last_update_utc": "Mon, 08 Jun 2026 00:02:31 +0000",
                  "base_code": "USD",
                  "rates": {
                    "USD": 1,
                    "EGP": 51.81532
                  }
                }
                """;
    }
}
