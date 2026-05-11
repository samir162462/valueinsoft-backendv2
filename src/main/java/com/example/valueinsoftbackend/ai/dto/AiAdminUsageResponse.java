package com.example.valueinsoftbackend.ai.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record AiAdminUsageResponse(
        Instant generatedAt,
        LocalDate fromDate,
        LocalDate toDate,
        long totalRequests,
        long totalTokens,
        BigDecimal estimatedCost,
        BigDecimal averageLatencyMs,
        long companiesNearLimit,
        List<AiAdminUsageCompanyDto> companies
) {
}
