package com.example.valueinsoftbackend.Model.HR;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Date;
import java.sql.Timestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceDay {
    private long id;
    private int companyId;
    private int branchId;
    private int employeeId;
    private Date attendanceDate;
    private Timestamp clockIn;
    private Timestamp clockOut;
    private int workingMinutes;
    private int breakMinutes;
    private int lateMinutes;
    private int overtimeMinutes;
    private String status;
    private Timestamp createdAt;
    private Timestamp updatedAt;
}
