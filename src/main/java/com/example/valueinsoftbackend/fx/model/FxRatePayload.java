package com.example.valueinsoftbackend.fx.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FxRatePayload(
        String baseCurrency,
        String targetCurrency,
        BigDecimal rate,
        String rateType,
        LocalDate effectiveDate,
        String sourceDescription,
        BigDecimal confidence
) {
}
