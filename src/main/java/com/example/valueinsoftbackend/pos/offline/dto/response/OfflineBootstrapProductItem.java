package com.example.valueinsoftbackend.pos.offline.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record OfflineBootstrapProductItem(
        Long productId,
        String barcode,
        String name,
        BigDecimal price,
        BigDecimal lowestPrice,
        Integer currentStock,
        String category,
        Boolean active,
        String uomCode,
        String pricingPolicyCode,
        Instant updatedAt
) {
}
