package com.example.valueinsoftbackend.Model.HR;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record AttendanceMonthDay(
        LocalDate date,
        Map<String, Long> counts,
        List<AttendanceMonthEmployee> employees
) {
}
