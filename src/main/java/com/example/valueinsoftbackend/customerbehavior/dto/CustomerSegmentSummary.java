package com.example.valueinsoftbackend.customerbehavior.dto;

import java.math.BigDecimal;

public record CustomerSegmentSummary(
        CustomerSegment segment,
        String label,
        long customerCount,
        BigDecimal netSpend,
        BigDecimal averageOrderValue,
        BigDecimal repeatPurchaseRate
) {
}
