package com.example.valueinsoftbackend.Model.Payroll;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Date;
import java.sql.Timestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayrollAttendanceSnapshot {
    private long id;
    private int companyId;
    private long payrollRunId;
    private long payrollRunLineId;
    private int employeeId;
    private int userId;
    private int branchId;
    private Date attendanceDate;
    private Integer shiftId;
    private int scheduledMinutes;
    private int workedMinutes;
    private int breakMinutes;
    private int lateMinutes;
    private int overtimeMinutes;
    private int payableMinutes;
    private String dayStatus;
    private boolean paidLeave;
    private Long sourceAttendanceDayId;
    private Timestamp createdAt;
}
