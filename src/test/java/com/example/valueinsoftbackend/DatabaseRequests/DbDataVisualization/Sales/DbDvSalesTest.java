/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.DatabaseRequests.DbDataVisualization.Sales;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DbDvSalesTest {

    @Test
    void currentPeriodEndsAtCurrentMoment() {
        LocalDateTime naturalEnd = LocalDateTime.of(2026, 8, 1, 0, 0);
        LocalDateTime now = LocalDateTime.of(2026, 7, 22, 14, 30);

        assertEquals(now, DbDvSales.boundedPeriodEnd(naturalEnd, now));
    }

    @Test
    void historicalPeriodKeepsItsNaturalEnd() {
        LocalDateTime naturalEnd = LocalDateTime.of(2025, 8, 1, 0, 0);
        LocalDateTime now = LocalDateTime.of(2026, 7, 22, 14, 30);

        assertEquals(naturalEnd, DbDvSales.boundedPeriodEnd(naturalEnd, now));
    }
}
