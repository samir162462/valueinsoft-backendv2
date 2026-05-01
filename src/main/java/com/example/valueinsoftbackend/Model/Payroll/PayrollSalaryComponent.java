package com.example.valueinsoftbackend.Model.Payroll;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.sql.Timestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayrollSalaryComponent {
    private long id;
    private int companyId;
    private long salaryProfileId;
    private String componentType;   // ALLOWANCE | DEDUCTION
    private Integer allowanceTypeId;
    private Integer deductionTypeId;
    private String calcMethod;      // FIXED | PERCENTAGE
    private BigDecimal amount;
    private BigDecimal percentage;
    private boolean isActive;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private String createdBy;
    private String updatedBy;
}
