package com.example.valueinsoftbackend.ai.dto;

import java.math.BigDecimal;

public record AiAdminUsageCompanyDto(
        long companyId,
        long requestCount,
        long promptTokens,
        long completionTokens,
        long totalTokens,
        BigDecimal estimatedCost,
        BigDecimal estimatedCostUsd,
        BigDecimal averageLatencyMs,
        boolean nearMonthlyTokenLimit
) {
}
