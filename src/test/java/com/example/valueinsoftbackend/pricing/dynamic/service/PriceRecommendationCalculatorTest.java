package com.example.valueinsoftbackend.pricing.dynamic.service;

import com.example.valueinsoftbackend.pricing.dynamic.model.DynamicPricingPolicy;
import com.example.valueinsoftbackend.pricing.dynamic.model.PriceReasonCode;
import com.example.valueinsoftbackend.pricing.dynamic.model.PriceRecommendationStatus;
import com.example.valueinsoftbackend.pricing.dynamic.model.PricingMetricsSnapshot;
import com.example.valueinsoftbackend.pricing.dynamic.model.ProductMovementClass;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PriceRecommendationCalculatorTest {

    private final PriceRecommendationCalculator calculator = new PriceRecommendationCalculator();

    @Test
    void calculateRaisesPriceToRecoverTargetMarginWithinPolicyCap() {
        var recommendation = calculator.calculate(
                metrics(new BigDecimal("100.0000"), new BigDecimal("90.0000"), new BigDecimal("95.0000"),
                        ProductMovementClass.STABLE, new BigDecimal("20.0000"), null),
                policy(new BigDecimal("0.2000"), new BigDecimal("0.1000"), new BigDecimal("0.1000"), new BigDecimal("0.1000"))
        );

        assertEquals(PriceRecommendationStatus.WARNING, recommendation.status());
        assertEquals(new BigDecimal("110.0000"), recommendation.suggestedRetailPrice());
        assertTrue(recommendation.reasonCodes().contains(PriceReasonCode.TARGET_MARGIN_ALIGNMENT));
        assertTrue(recommendation.warningCodes().contains(PriceReasonCode.MAX_INCREASE_BLOCKED));
    }

    @Test
    void calculateSkipsWhenPriceOrCostDataIsMissing() {
        var recommendation = calculator.calculate(
                metrics(BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("20.0000"),
                        ProductMovementClass.UNKNOWN, BigDecimal.ZERO, null),
                policy(new BigDecimal("0.2000"), new BigDecimal("0.1000"), new BigDecimal("0.1000"), new BigDecimal("0.1000"))
        );

        assertEquals(PriceRecommendationStatus.SKIPPED, recommendation.status());
        assertTrue(recommendation.reasonCodes().contains(PriceReasonCode.INSUFFICIENT_DATA));
    }

    @Test
    void calculateDiscountsDeadOverstockWithoutDroppingBelowMinimumMargin() {
        var recommendation = calculator.calculate(
                metrics(new BigDecimal("100.0000"), new BigDecimal("95.0000"), new BigDecimal("60.0000"),
                        ProductMovementClass.DEAD, new BigDecimal("120.0000"), BigDecimal.ZERO),
                policy(new BigDecimal("0.2000"), new BigDecimal("0.1000"), new BigDecimal("0.1000"), new BigDecimal("0.1000"))
        );

        assertEquals(PriceRecommendationStatus.RECOMMENDED, recommendation.status());
        assertEquals(new BigDecimal("95.0000"), recommendation.suggestedRetailPrice());
        assertTrue(recommendation.reasonCodes().contains(PriceReasonCode.DEAD_STOCK_CLEARANCE));
    }

    private DynamicPricingPolicy policy(BigDecimal targetMargin, BigDecimal minMargin,
                                        BigDecimal maxIncrease, BigDecimal maxDecrease) {
        return new DynamicPricingPolicy(
                1L,
                1L,
                2L,
                "BRANCH",
                null,
                "Test policy",
                targetMargin,
                minMargin,
                maxIncrease,
                maxDecrease,
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
                true,
                "system",
                null,
                null,
                null
        );
    }

    private PricingMetricsSnapshot metrics(BigDecimal retailPrice, BigDecimal lowestPrice, BigDecimal buyingPrice,
                                           ProductMovementClass movementClass, BigDecimal daysCover,
                                           BigDecimal costChangePct) {
        return new PricingMetricsSnapshot(
                10L,
                "Test product",
                "Category",
                null,
                null,
                null,
                null,
                retailPrice,
                lowestPrice,
                buyingPrice,
                new BigDecimal("10.0000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                daysCover,
                retailPrice.compareTo(BigDecimal.ZERO) > 0
                        ? retailPrice.subtract(buyingPrice).divide(retailPrice, 4, java.math.RoundingMode.HALF_UP)
                        : null,
                movementClass,
                BigDecimal.ZERO,
                costChangePct,
                null
        );
    }
}
