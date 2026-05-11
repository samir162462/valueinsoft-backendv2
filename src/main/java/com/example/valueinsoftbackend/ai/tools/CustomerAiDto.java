package com.example.valueinsoftbackend.ai.tools;

import java.time.LocalDateTime;

public record CustomerAiDto(
        Long customerId,
        String customerName,
        String maskedPhone,
        Long branchId,
        LocalDateTime registeredAt
) {
}
