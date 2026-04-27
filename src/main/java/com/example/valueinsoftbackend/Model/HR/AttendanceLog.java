package com.example.valueinsoftbackend.Model.HR;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceLog {
    private long id;
    private int companyId;
    private int branchId;
    private int employeeId;
    private String actionType;
    private Timestamp actionTime;
    private String source;
    private String deviceId;
    private String ipAddress;
    private String userAgent;
    private String remarks;
    private String correctionReason;
    private String managerId;
    private Timestamp createdAt;
    private String createdBy;
}
