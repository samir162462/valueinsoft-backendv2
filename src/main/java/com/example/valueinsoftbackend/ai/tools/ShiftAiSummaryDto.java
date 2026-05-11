package com.example.valueinsoftbackend.ai.tools;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ShiftAiSummaryDto(
        Long shiftId,
        Long branchId,
        String status,
        String openedBy,
        String assignedCashier,
        LocalDateTime openedAt,
        LocalDateTime closedAt,
        Long orderCount,
        BigDecimal grossSales,
        BigDecimal discountTotal,
        BigDecimal netSales,
        BigDecimal expectedCash,
        BigDecimal openingFloat
) {
}
