package com.example.valueinsoftbackend.ai.tools;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public record AiToolDateRange(LocalDate fromDate, LocalDate toDate) {

    public AiToolDateRange {
        if (fromDate == null || toDate == null) {
            throw new IllegalArgumentException("Date range is required");
        }
        if (toDate.isBefore(fromDate)) {
            throw new IllegalArgumentException("Date range is invalid");
        }
        if (ChronoUnit.DAYS.between(fromDate, toDate) > 30) {
            throw new IllegalArgumentException("Date range cannot exceed 31 days");
        }
    }

    public static AiToolDateRange today() {
        LocalDate today = LocalDate.now();
        return new AiToolDateRange(today, today);
    }
}
