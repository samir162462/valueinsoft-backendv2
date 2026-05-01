package com.example.valueinsoftbackend.Model.Payroll;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayrollAdjustment {
    private long id;
    private int companyId;
    private Integer branchId;
    private int employeeId;
    private Long payrollRunId;         // set when status = APPLIED
    private String adjustmentType;     // ALLOWANCE | DEDUCTION
    private String adjustmentCode;
    private String description;
    private BigDecimal amount;
    private Date effectiveDate;
    private String status;             // PENDING | APPROVED | REJECTED | APPLIED | CANCELLED
    private String approvedBy;
    private Timestamp approvedAt;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private String createdBy;
    private String updatedBy;
}
