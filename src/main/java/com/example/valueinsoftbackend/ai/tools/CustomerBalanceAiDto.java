package com.example.valueinsoftbackend.ai.tools;

import java.math.BigDecimal;

public record CustomerBalanceAiDto(
        Long customerId,
        String customerName,
        BigDecimal orderTotal,
        BigDecimal receiptTotal,
        BigDecimal balance
) {
}
