package com.example.valueinsoftbackend.ai.tools;

import java.math.BigDecimal;

public record PaymentBreakdownDto(
        String paymentType,
        Long transactionCount,
        BigDecimal totalAmount
) {
}
