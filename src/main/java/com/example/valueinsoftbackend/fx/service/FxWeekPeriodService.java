package com.example.valueinsoftbackend.fx.service;

import com.example.valueinsoftbackend.Config.FxDeepSeekProperties;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;

@Service
public class FxWeekPeriodService {

    private final FxDeepSeekProperties properties;

    public FxWeekPeriodService(FxDeepSeekProperties properties) {
        this.properties = properties;
    }

    public LocalDate currentWeekStart() {
        return weekStart(Instant.now());
    }

    public LocalDate weekStart(Instant instant) {
        ZoneId zone = ZoneId.of(properties.getSchedule().getTimeZone());
        ZonedDateTime zonedDateTime = ZonedDateTime.ofInstant(instant, zone);
        return zonedDateTime.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
    }

    public ZoneId zoneId() {
        return ZoneId.of(properties.getSchedule().getTimeZone());
    }
}
