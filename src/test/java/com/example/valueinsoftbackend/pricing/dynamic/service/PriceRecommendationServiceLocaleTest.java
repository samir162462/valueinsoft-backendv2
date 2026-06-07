package com.example.valueinsoftbackend.pricing.dynamic.service;

import com.example.valueinsoftbackend.pricing.dynamic.dto.PriceRecommendationRunRequest;
import com.example.valueinsoftbackend.pricing.dynamic.dto.PriceRecommendationRunResponse;
import com.example.valueinsoftbackend.pricing.dynamic.repository.DynamicPricingPolicyRepository;
import com.example.valueinsoftbackend.pricing.dynamic.repository.PriceRecommendationRunRepository;
import com.example.valueinsoftbackend.pricing.dynamic.repository.PricingMetricsRepository;
import com.example.valueinsoftbackend.pricing.dynamic.security.DynamicPricingSecurityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PriceRecommendationServiceLocaleTest {

    @Mock
    private PricingMetricsRepository metricsRepository;

    @Mock
    private DynamicPricingPolicyRepository policyRepository;

    @Mock
    private PriceRecommendationRunRepository runRepository;

    @Mock
    private PriceRecommendationCalculator calculator;

    @Mock
    private DynamicPricingSecurityService securityService;

    @Mock
    private PricingAuditService auditService;

    @InjectMocks
    private PriceRecommendationService service;

    @Test
    void createRunFormatsScopeJsonWithAsciiDigitsWhenDefaultLocaleUsesArabicDigits() {
        Locale previousLocale = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("ar-EG"));
        try {
            PriceRecommendationRunRequest request = new PriceRecommendationRunRequest(
                    1095,
                    2,
                    30,
                    LocalDate.of(2026, 6, 8),
                    null,
                    List.of(10L, 11L),
                    null,
                    null,
                    null,
                    null,
                    null,
                    50
            );
            when(runRepository.createRun(eq(1095), eq(2), eq(30), anyString(), anyString(), anyString()))
                    .thenReturn(123L);
            when(metricsRepository.findMetrics(any()))
                    .thenReturn(new PricingMetricsRepository.MetricsPage(List.of(), 0, 50, 0, 0));
            when(runRepository.findRun(1095, 123L))
                    .thenReturn(new PriceRecommendationRunResponse(
                            123L,
                            1095,
                            2,
                            "COMPLETED",
                            30,
                            0,
                            0,
                            0,
                            0,
                            "owner@example.com",
                            null,
                            null,
                            null
                    ));

            service.createRun("owner@example.com", request);

            ArgumentCaptor<String> scopeJson = ArgumentCaptor.forClass(String.class);
            verify(runRepository).createRun(anyInt(), anyInt(), anyInt(), anyString(), scopeJson.capture(), anyString());
            assertTrue(scopeJson.getValue().contains("\"companyId\":1095"));
            assertTrue(scopeJson.getValue().contains("\"productIdsCount\":2"));
            assertFalse(scopeJson.getValue().contains("١٠٩٥"));
        } finally {
            Locale.setDefault(previousLocale);
        }
    }
}
