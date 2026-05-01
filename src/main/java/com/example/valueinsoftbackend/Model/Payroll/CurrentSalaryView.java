package com.example.valueinsoftbackend.Model.Payroll;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.sql.Date;

/**
 * Read-model for the "Current Salaries" screen.
 * Aggregates hr_employee + payroll_salary_profile + payroll_salary_component.
 * setupStatus: "CONFIGURED" when an active salary profile exists, "MISSING" otherwise.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CurrentSalaryView {
    private int employeeId;
    private String employeeCode;
    private String firstName;
    private String lastName;
    private int branchId;
    private String jobTitle;
    private String salaryType;
    private BigDecimal baseSalary;
    private String payrollFrequency;
    private String currencyCode;
    private BigDecimal totalAllowances;
    private BigDecimal totalDeductions;
    private BigDecimal expectedNetSalary;
    private boolean profileIsActive;
    private Date effectiveFrom;
    private Date effectiveTo;
    private Long salaryProfileId;      // null when MISSING
    private String setupStatus;        // CONFIGURED | MISSING
}
