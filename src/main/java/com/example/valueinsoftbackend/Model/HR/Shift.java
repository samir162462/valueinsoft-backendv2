package com.example.valueinsoftbackend.Model.HR;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Time;
import java.sql.Timestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Shift {
    private int id;
    private int companyId;
    private int branchId;
    private String shiftName;
    private Time startTime;
    private Time endTime;
    private int graceMinutes;
    private boolean isActive;
    private Timestamp createdAt;
    private String createdBy;
    private Timestamp updatedAt;
    private String updatedBy;
}
