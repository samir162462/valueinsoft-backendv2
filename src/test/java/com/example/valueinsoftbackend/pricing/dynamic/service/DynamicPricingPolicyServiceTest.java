package com.example.valueinsoftbackend.pricing.dynamic.service;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.pricing.dynamic.dto.DynamicPricingPolicyRequest;
import com.example.valueinsoftbackend.pricing.dynamic.repository.DynamicPricingPolicyRepository;
import com.example.valueinsoftbackend.pricing.dynamic.security.DynamicPricingSecurityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DynamicPricingPolicyServiceTest {

    @Mock
    private DynamicPricingPolicyRepository repository;

    @Mock
    private DynamicPricingSecurityService securityService;

    @Mock
    private PricingAuditService auditService;

    @InjectMocks
    private DynamicPricingPolicyService service;

    @Test
    void savePolicyRejectsTargetMarginBelowMinimumMargin() {
        DynamicPricingPolicyRequest request = baseRequest(
                new BigDecimal("0.0500"),
                new BigDecimal("0.1000")
        );

        assertThrows(ApiException.class, () -> service.savePolicy("owner@example.com", request));
        verify(securityService).requirePolicyManage("owner@example.com", 1, 2);
    }

    @Test
    void savePolicyRejectsDeadStockDaysBeforeSlowMovingDays() {
        DynamicPricingPolicyRequest request = new DynamicPricingPolicyRequest(
                null,
                1L,
                2L,
                "BRANCH",
                null,
                "Branch policy",
                new BigDecimal("0.2000"),
                new BigDecimal("0.1000"),
                new BigDecimal("0.0500"),
                new BigDecimal("0.1000"),
                null,
                null,
                null,
                null,
                false,
                true,
                true,
                false,
                null,
                500,
                "NEAREST_1",
                new BigDecimal("7.0000"),
                new BigDecimal("60.0000"),
                90,
                45,
                "{}",
                true
        );

        assertThrows(ApiException.class, () -> service.savePolicy("owner@example.com", request));
        verify(securityService).requirePolicyManage("owner@example.com", 1, 2);
    }

    private DynamicPricingPolicyRequest baseRequest(BigDecimal targetMargin, BigDecimal minMargin) {
        return new DynamicPricingPolicyRequest(
                null,
                1L,
                2L,
                "BRANCH",
                null,
                "Branch policy",
                targetMargin,
                minMargin,
                new BigDecimal("0.0500"),
                new BigDecimal("0.1000"),
                null,
                null,
                null,
                null,
                false,
                true,
                true,
                false,
                null,
                500,
                "NEAREST_1",
                new BigDecimal("7.0000"),
                new BigDecimal("60.0000"),
                45,
                120,
                "{}",
                true
        );
    }
}
