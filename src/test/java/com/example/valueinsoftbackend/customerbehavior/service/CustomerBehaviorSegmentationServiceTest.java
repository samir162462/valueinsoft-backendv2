package com.example.valueinsoftbackend.customerbehavior.service;

import com.example.valueinsoftbackend.customerbehavior.dto.CustomerBehaviorConfig;
import com.example.valueinsoftbackend.customerbehavior.dto.CustomerSegment;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomerBehaviorSegmentationServiceTest {

    private final CustomerBehaviorSegmentationService service = new CustomerBehaviorSegmentationService();
    private final CustomerBehaviorConfig config = CustomerBehaviorConfig.defaults("EGP", "Africa/Cairo");

    @Test
    void returnRiskHasHighestPriority() {
        CustomerBehaviorSegmentationService.SegmentDecision decision = service.classify(input(
                8,
                8,
                BigDecimal.valueOf(100_000),
                BigDecimal.ZERO,
                BigDecimal.valueOf(0.20),
                BigDecimal.valueOf(0.70),
                15L,
                200L
        ), config);

        assertEquals(CustomerSegment.RETURN_RISK, decision.primary());
        assertTrue(decision.secondaryFlags().contains(CustomerSegment.VIP));
        assertTrue(decision.secondaryFlags().contains(CustomerSegment.CATEGORY_LOYAL));
    }

    @Test
    void dormantBeatsVipWhenCustomerIsInactiveTooLong() {
        CustomerBehaviorSegmentationService.SegmentDecision decision = service.classify(input(
                10,
                10,
                BigDecimal.valueOf(120_000),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                220L,
                400L
        ), config);

        assertEquals(CustomerSegment.DORMANT, decision.primary());
        assertTrue(decision.secondaryFlags().contains(CustomerSegment.VIP));
    }

    @Test
    void recentNoPurchaseCustomerIsNew() {
        CustomerBehaviorSegmentationService.SegmentDecision decision = service.classify(input(
                0,
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                7L
        ), config);

        assertEquals(CustomerSegment.NEW, decision.primary());
    }

    @Test
    void oldNoPurchaseCustomerIsDormant() {
        CustomerBehaviorSegmentationService.SegmentDecision decision = service.classify(input(
                0,
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                120L
        ), config);

        assertEquals(CustomerSegment.DORMANT, decision.primary());
    }

    private CustomerBehaviorSegmentationService.CustomerBehaviorClassificationInput input(
            long orders,
            long historicalOrders,
            BigDecimal netSpend,
            BigDecimal discountRatio,
            BigDecimal returnRatio,
            BigDecimal categoryConcentration,
            Long daysSinceLastPurchase,
            Long daysSinceRegistration) {
        return new CustomerBehaviorSegmentationService.CustomerBehaviorClassificationInput(
                orders,
                historicalOrders,
                netSpend,
                discountRatio,
                returnRatio,
                categoryConcentration,
                daysSinceLastPurchase,
                daysSinceRegistration
        );
    }
}
