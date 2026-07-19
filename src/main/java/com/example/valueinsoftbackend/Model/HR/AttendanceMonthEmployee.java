package com.example.valueinsoftbackend.Model.HR;

import java.sql.Timestamp;

public record AttendanceMonthEmployee(
        int userId,
        int employeeId,
        int branchId,
        String firstName,
        String lastName,
        String shiftName,
        String classification,
        double attendancePercentage,
        int scheduledMinutes,
        int workingMinutes,
        int breakMinutes,
        int lateMinutes,
        int overtimeMinutes,
        int sessionCount,
        Timestamp clockIn,
        Timestamp clockOut
) {
}
