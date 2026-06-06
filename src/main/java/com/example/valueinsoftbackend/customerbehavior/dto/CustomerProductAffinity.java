package com.example.valueinsoftbackend.customerbehavior.dto;

import java.math.BigDecimal;

public record CustomerProductAffinity(
        long productAId,
        String productAName,
        long productBId,
        String productBName,
        long supportOrders,
        BigDecimal confidence
) {
}
