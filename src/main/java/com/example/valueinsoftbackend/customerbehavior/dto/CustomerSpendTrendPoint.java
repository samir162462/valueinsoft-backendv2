package com.example.valueinsoftbackend.customerbehavior.dto;

import java.math.BigDecimal;

public record CustomerSpendTrendPoint(
        String month,
        long orderCount,
        BigDecimal netSpend
) {
}
