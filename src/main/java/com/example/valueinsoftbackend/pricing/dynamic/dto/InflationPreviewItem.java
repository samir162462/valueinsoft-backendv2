package com.example.valueinsoftbackend.pricing.dynamic.dto;

import java.math.BigDecimal;

public record InflationPreviewItem(
        long productId,
        String productName,
        BigDecimal oldBuyingPrice,
        BigDecimal newBuyingPrice,
        BigDecimal oldRetailPrice,
        BigDecimal newRetailPrice,
        BigDecimal oldLowestPrice,
        BigDecimal newLowestPrice
) {}
