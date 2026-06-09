package com.example.valueinsoftbackend.fx.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record GlobalFxRateSnapshot(
        long id,
        String baseCurrency,
        String targetCurrency,
        LocalDate weekStartDate,
        LocalDate effectiveDate,
        BigDecimal rate,
        String rateType,
        String sourceCode,
        String sourceDescription,
        BigDecimal confidence,
        OffsetDateTime requestTimestamp,
        OffsetDateTime responseTimestamp,
        String rawResponse,
        String status,
        String validationStatus,
        String validationMessage,
        boolean initialRate,
        boolean scheduledRate,
        FxRefreshTrigger triggerType,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
