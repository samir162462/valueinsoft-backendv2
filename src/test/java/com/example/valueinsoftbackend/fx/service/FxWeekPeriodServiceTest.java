package com.example.valueinsoftbackend.fx.service;

import com.example.valueinsoftbackend.Config.FxDeepSeekProperties;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FxWeekPeriodServiceTest {

    @Test
    void calculatesSundayWeekStartInAfricaCairo() {
        FxDeepSeekProperties properties = new FxDeepSeekProperties();
        properties.getSchedule().setTimeZone("Africa/Cairo");
        FxWeekPeriodService service = new FxWeekPeriodService(properties);

        assertEquals(LocalDate.of(2026, 6, 7), service.weekStart(Instant.parse("2026-06-08T10:00:00Z")));
        assertEquals(LocalDate.of(2026, 6, 7), service.weekStart(Instant.parse("2026-06-07T00:30:00Z")));
    }
}
