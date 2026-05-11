package com.example.valueinsoftbackend.ai.tools;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SupplierInvoiceAiDto(
        Long documentId,
        Long supplierId,
        Long productId,
        Integer quantity,
        BigDecimal totalCost,
        BigDecimal paidAmount,
        BigDecimal remainingAmount,
        LocalDateTime createdAt,
        String description
) {
}
