package com.example.valueinsoftbackend.Model.Payroll;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayrollSalaryProfile {
    private long id;
    private int companyId;
    private int employeeId;
    private int branchId;
    private String jobTitle;
    private String salaryType;
    private BigDecimal baseSalary;
    private String currencyCode;
    private String payrollFrequency;
    private UUID salaryExpenseAccountId;   // optional per-profile override
    private UUID salaryPayableAccountId;   // optional per-profile override
    private boolean isActive;
    private Date effectiveFrom;
    private Date effectiveTo;
    private int version;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private String createdBy;
    private String updatedBy;
}
