package com.example.valueinsoftbackend.ai.tools;

import java.math.BigDecimal;

public record SupplierAiDto(
        Long supplierId,
        String supplierName,
        String maskedPhone,
        String major,
        BigDecimal balance
) {
}
