package com.example.valueinsoftbackend.fx.service;

import com.example.valueinsoftbackend.Config.FxDeepSeekProperties;
import com.example.valueinsoftbackend.ai.provider.AiProviderException;
import com.example.valueinsoftbackend.ai.provider.DeepSeekAiProvider;
import com.example.valueinsoftbackend.ai.service.AiModelRequest;
import com.example.valueinsoftbackend.ai.service.AiModelResponse;
import com.example.valueinsoftbackend.fx.model.FxDeepSeekFetchResult;
import com.example.valueinsoftbackend.fx.model.FxRatePayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeepSeekFxRateProviderTest {

    @Test
    void parsesFencedJsonResponse() {
        DeepSeekFxRateProvider provider = new DeepSeekFxRateProvider(
                mock(DeepSeekAiProvider.class),
                new ObjectMapper(),
                properties()
        );

        FxRatePayload payload = provider.parsePayload("""
                ```json
                {
                  "baseCurrency": "USD",
                  "targetCurrency": "EGP",
                  "rate": 50.250000,
                  "rateType": "REFERENCE",
                  "effectiveDate": "2026-06-08",
                  "sourceDescription": "DeepSeek reference",
                  "confidence": 0.91
                }
                ```
                """);

        assertEquals("USD", payload.baseCurrency());
        assertEquals("EGP", payload.targetCurrency());
        assertEquals(0, new BigDecimal("50.250000").compareTo(payload.rate()));
        assertEquals(LocalDate.of(2026, 6, 8), payload.effectiveDate());
    }

    @Test
    void rejectsUnrelatedTextAroundJson() {
        DeepSeekFxRateProvider provider = new DeepSeekFxRateProvider(
                mock(DeepSeekAiProvider.class),
                new ObjectMapper(),
                properties()
        );

        assertThrows(FxDeepSeekRateException.class, () -> provider.parsePayload("""
                Here is the rate:
                {"baseCurrency":"USD","targetCurrency":"EGP","rate":50,"rateType":"REFERENCE","effectiveDate":"2026-06-08","sourceDescription":"x","confidence":0.9}
                """));
    }

    @Test
    void retriesTransientDeepSeekTimeoutAndReturnsOneRate() {
        DeepSeekAiProvider deepSeek = mock(DeepSeekAiProvider.class);
        when(deepSeek.generate(any(AiModelRequest.class)))
                .thenThrow(new AiProviderException(
                        AiProviderException.Category.PROVIDER_TIMEOUT,
                        "deepseek",
                        "DeepSeek response timed out."))
                .thenReturn(new AiModelResponse("""
                        {"baseCurrency":"USD","targetCurrency":"EGP","rate":50.0,"rateType":"REFERENCE","effectiveDate":"2026-06-08","sourceDescription":"x","confidence":0.9}
                        """, "deepseek-chat", false));

        FxDeepSeekFetchResult result = new DeepSeekFxRateProvider(deepSeek, new ObjectMapper(), properties()).fetchRate();

        assertEquals(new BigDecimal("50.0"), result.payload().rate());
        verify(deepSeek, times(2)).generate(any(AiModelRequest.class));
    }

    @Test
    void promptRequiresTodayEffectiveDate() {
        DeepSeekAiProvider deepSeek = mock(DeepSeekAiProvider.class);
        when(deepSeek.generate(any(AiModelRequest.class))).thenReturn(new AiModelResponse("""
                {"baseCurrency":"USD","targetCurrency":"EGP","rate":50.0,"rateType":"REFERENCE","effectiveDate":"%s","sourceDescription":"x","confidence":0.9}
                """.formatted(LocalDate.now(ZoneId.of("Africa/Cairo"))), "deepseek-chat", false));
        FxDeepSeekProperties properties = properties();

        new DeepSeekFxRateProvider(deepSeek, new ObjectMapper(), properties).fetchRate();

        ArgumentCaptor<AiModelRequest> requestCaptor = ArgumentCaptor.forClass(AiModelRequest.class);
        verify(deepSeek).generate(requestCaptor.capture());
        String prompt = requestCaptor.getValue().userMessage();
        LocalDate today = LocalDate.now(ZoneId.of("Africa/Cairo"));
        assertTrue(prompt.contains("Backend validation date: " + today));
        assertTrue(prompt.contains("The effectiveDate must equal " + today + " exactly."));
        assertTrue(prompt.contains("Do not return stale, historical, weekend, previous-business-day, cutoff, or forecast rates."));
    }

    private FxDeepSeekProperties properties() {
        FxDeepSeekProperties properties = new FxDeepSeekProperties();
        properties.getRetry().setMaxAttempts(2);
        properties.getRetry().setInitialDelaySeconds(0);
        return properties;
    }
}
