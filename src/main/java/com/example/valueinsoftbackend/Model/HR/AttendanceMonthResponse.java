package com.example.valueinsoftbackend.Model.HR;

import java.util.List;

public record AttendanceMonthResponse(
        String month,
        int employeeCount,
        List<AttendanceMonthDay> days
) {
}
