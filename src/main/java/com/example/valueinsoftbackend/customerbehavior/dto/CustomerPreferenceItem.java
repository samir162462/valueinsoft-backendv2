package com.example.valueinsoftbackend.customerbehavior.dto;

import java.math.BigDecimal;

public record CustomerPreferenceItem(
        String name,
        String type,
        long customerCount,
        long orderCount,
        BigDecimal quantity,
        BigDecimal netSpend
) {
}
