package com.example.valueinsoftbackend.Model.HR;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Date;
import java.sql.Timestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeShift {
    private int id;
    private int companyId;
    private int branchId;
    private int employeeId;
    private int shiftId;
    private Date effectiveFrom;
    private Date effectiveTo;
    private Timestamp createdAt;
    private String createdBy;
    private Timestamp updatedAt;
    private String updatedBy;
}
