package com.example.valueinsoftbackend.pos.offline.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record OfflineBootstrapPriceItem(
        Long productId,
        BigDecimal retailPrice,
        BigDecimal lowestPrice,
        BigDecimal buyingPrice,
        String pricingPolicyCode,
        String pricingStrategyType,
        String pricingConfigJson,
        Instant updatedAt
) {
}
