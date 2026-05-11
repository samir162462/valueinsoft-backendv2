package com.example.valueinsoftbackend.ai.tools;

import java.math.BigDecimal;

public record SalesAiCashierDto(
        String cashierName,
        Long orderCount,
        BigDecimal salesTotal,
        BigDecimal incomeTotal
) {
}
