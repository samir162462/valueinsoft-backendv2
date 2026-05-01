package com.example.valueinsoftbackend.Model.Payroll;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.sql.Timestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayrollRunLine {
    private long id;
    private int companyId;
    private long payrollRunId;
    private int employeeId;
    private long salaryProfileId;
    private BigDecimal baseSalary;
    private BigDecimal totalAllowances;
    private BigDecimal totalDeductions;
    private BigDecimal grossSalary;
    private BigDecimal netSalary;
    private BigDecimal paidAmount;
    private BigDecimal remainingAmount;
    private String paymentStatus;         // UNPAID | PARTIALLY_PAID | PAID
    private int workingDays;
    private int absentDays;
    private int lateMinutes;
    private int overtimeMinutes;
    private String salaryType;            // snapshot at calculation time
    private String payrollFrequency;      // snapshot at calculation time
    private String currencyCode;          // snapshot at calculation time
    private String calculationSnapshotJson;
    private String notes;
    private Timestamp createdAt;
    private Timestamp updatedAt;
}
