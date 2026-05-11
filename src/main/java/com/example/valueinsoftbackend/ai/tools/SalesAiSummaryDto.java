package com.example.valueinsoftbackend.ai.tools;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SalesAiSummaryDto(
        Long branchId,
        LocalDate fromDate,
        LocalDate toDate,
        Long orderCount,
        BigDecimal grossSales,
        BigDecimal discountTotal,
        BigDecimal netSales,
        BigDecimal incomeTotal,
        BigDecimal refundTotal
) {
}
