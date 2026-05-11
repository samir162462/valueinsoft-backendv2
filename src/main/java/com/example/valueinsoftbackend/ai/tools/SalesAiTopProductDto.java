package com.example.valueinsoftbackend.ai.tools;

import java.math.BigDecimal;

public record SalesAiTopProductDto(
        Long productId,
        String productName,
        Long quantitySold,
        BigDecimal salesTotal
) {
}
