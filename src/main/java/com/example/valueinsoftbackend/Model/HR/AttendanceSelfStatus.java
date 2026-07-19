package com.example.valueinsoftbackend.Model.HR;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceSelfStatus {
    private int userId;
    private int employeeId;
    private int companyId;
    private int branchId;
    private LocalDate attendanceDate;
    private boolean eligible;
    private String ineligibleReason;
    private Timestamp clockIn;
    private Timestamp clockOut;
    private boolean onBreak;
    private boolean completed;
    private String status;
    private String primaryAction;
    private List<String> availableActions;
    private Integer shiftId;
    private String shiftName;
    private Timestamp scheduledShiftStart;
    private Timestamp scheduledShiftEnd;
    private int clockOutGraceMinutes;
    private boolean shiftEndingSoon;
    private int sessionCount;
    private int workingMinutes;
    private int breakMinutes;
    private Timestamp calculatedAt;
}
