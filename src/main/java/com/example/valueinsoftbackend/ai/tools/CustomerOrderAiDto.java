package com.example.valueinsoftbackend.ai.tools;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CustomerOrderAiDto(
        Long orderId,
        LocalDateTime orderTime,
        String orderType,
        BigDecimal orderTotal,
        BigDecimal orderDiscount,
        BigDecimal netTotal,
        String salesUser
) {
}
