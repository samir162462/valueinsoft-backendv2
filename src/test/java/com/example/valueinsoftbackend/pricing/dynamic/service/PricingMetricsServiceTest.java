package com.example.valueinsoftbackend.pricing.dynamic.service;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.pricing.dynamic.dto.PricingMetricsRequest;
import com.example.valueinsoftbackend.pricing.dynamic.repository.PricingMetricsRepository;
import com.example.valueinsoftbackend.pricing.dynamic.security.DynamicPricingSecurityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PricingMetricsServiceTest {

    @Mock
    private PricingMetricsRepository repository;

    @Mock
    private DynamicPricingSecurityService securityService;

    @InjectMocks
    private PricingMetricsService service;

    @Test
    void previewMetricsRejectsDateRangeOverLimit() {
        PricingMetricsRequest request = request(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 8, 1)
        );

        assertThrows(ApiException.class, () -> service.previewMetrics("owner@example.com", request));
    }

    @Test
    void previewMetricsHidesCostDetailsWhenCapabilityMissing() {
        PricingMetricsRequest request = request(
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 7)
        );
        when(securityService.canReadCost("manager@example.com", 1, 2)).thenReturn(false);
        when(repository.findMetrics(any())).thenReturn(new PricingMetricsRepository.MetricsPage(
                new ArrayList<>(),
                0,
                25,
                0,
                0
        ));

        var response = service.previewMetrics("manager@example.com", request);

        assertFalse(response.costDetailsIncluded());
        verify(securityService).requireView("manager@example.com", 1, 2);
    }

    @Test
    void previewMetricsIncludesCostDetailsWhenCapabilityPresent() {
        PricingMetricsRequest request = request(
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 7)
        );
        when(securityService.canReadCost("accountant@example.com", 1, 2)).thenReturn(true);
        when(repository.findMetrics(any())).thenReturn(new PricingMetricsRepository.MetricsPage(
                new ArrayList<>(),
                0,
                25,
                0,
                0
        ));

        var response = service.previewMetrics("accountant@example.com", request);

        assertTrue(response.costDetailsIncluded());
        verify(securityService).requireView("accountant@example.com", 1, 2);
    }

    private PricingMetricsRequest request(LocalDate fromDate, LocalDate toDate) {
        return new PricingMetricsRequest(
                1,
                2,
                fromDate,
                toDate,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                25
        );
    }
}
