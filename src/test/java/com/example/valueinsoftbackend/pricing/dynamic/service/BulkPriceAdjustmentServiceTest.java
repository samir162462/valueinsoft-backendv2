package com.example.valueinsoftbackend.pricing.dynamic.service;

import com.example.valueinsoftbackend.pricing.dynamic.model.DynamicPricingPolicy;
import com.example.valueinsoftbackend.pricing.dynamic.model.PriceAdjustmentDirection;
import com.example.valueinsoftbackend.pricing.dynamic.model.PriceAdjustmentMode;
import com.example.valueinsoftbackend.pricing.dynamic.model.PriceTarget;
import com.example.valueinsoftbackend.pricing.dynamic.repository.PriceAdjustmentBatchRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BulkPriceAdjustmentServiceTest {

    private final BulkPriceAdjustmentService service = new BulkPriceAdjustmentService(null, null, null, null);

    @Test
    void buildDraftCalculatesPercentageIncreaseForRetailOnly() {
        var draft = service.buildDraft(
                product(new BigDecimal("100.0000"), new BigDecimal("80.0000"), new BigDecimal("60.0000")),
                policy(new BigDecimal("0.5000"), new BigDecimal("0.5000"), false),
                PriceAdjustmentMode.PERCENTAGE,
                PriceAdjustmentDirection.INCREASE,
                new BigDecimal("5.0000"),
                PriceTarget.RETAIL,
                false
        );

        assertEquals(new BigDecimal("105.0000"), draft.newRetailPrice());
        assertEquals(new BigDecimal("80.0000"), draft.newLowestPrice());
        assertEquals("WARNING", draft.status());
        assertTrue(draft.warningCodes().contains("APPROVAL_REQUIRED"));
    }

    @Test
    void buildDraftBlocksFixedDecreaseBelowCost() {
        var draft = service.buildDraft(
                product(new BigDecimal("100.0000"), new BigDecimal("80.0000"), new BigDecimal("75.0000")),
                policy(new BigDecimal("0.5000"), new BigDecimal("0.5000"), false),
                PriceAdjustmentMode.FIXED_AMOUNT,
                PriceAdjustmentDirection.DECREASE,
                new BigDecimal("20.0000"),
                PriceTarget.RETAIL_AND_LOWEST,
                false
        );

        assertEquals("BLOCKED", draft.status());
        assertTrue(draft.blockedCodes().contains("BELOW_COST_BLOCKED"));
    }

    @Test
    void buildDraftUsesRecommendedPricesForRecommendationSource() {
        var draft = service.buildDraft(
                recommendationProduct(),
                policy(new BigDecimal("0.5000"), new BigDecimal("0.5000"), false),
                null,
                null,
                null,
                PriceTarget.RETAIL_AND_LOWEST,
                true
        );

        assertEquals(new BigDecimal("112.0000"), draft.newRetailPrice());
        assertEquals(new BigDecimal("90.0000"), draft.newLowestPrice());
        assertTrue(draft.reasonCodes().contains("RECOMMENDATION_PRICE"));
    }

    private PriceAdjustmentBatchRepository.AdjustmentProductRow product(BigDecimal retail, BigDecimal lowest, BigDecimal buying) {
        return new PriceAdjustmentBatchRepository.AdjustmentProductRow(
                10L,
                null,
                "Test product",
                retail,
                lowest,
                buying,
                null,
                null
        );
    }

    private PriceAdjustmentBatchRepository.AdjustmentProductRow recommendationProduct() {
        return new PriceAdjustmentBatchRepository.AdjustmentProductRow(
                10L,
                20L,
                "Test product",
                new BigDecimal("100.0000"),
                new BigDecimal("80.0000"),
                new BigDecimal("60.0000"),
                new BigDecimal("112.0000"),
                new BigDecimal("90.0000")
        );
    }

    private DynamicPricingPolicy policy(BigDecimal maxIncrease, BigDecimal maxDecrease, boolean allowBelowCost) {
        return new DynamicPricingPolicy(
                1L,
                1L,
                2L,
                "BRANCH",
                null,
                "Test policy",
                new BigDecimal("0.2000"),
                new BigDecimal("0.1000"),
                maxIncrease,
                maxDecrease,
                null,
                null,
                null,
                null,
                allowBelowCost,
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
}
