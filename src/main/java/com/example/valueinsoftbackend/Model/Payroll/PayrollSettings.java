package com.example.valueinsoftbackend.Model.Payroll;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayrollSettings {
    private int id;
    private int companyId;
    private String defaultCurrency;
    private String defaultFrequency;
    private boolean autoIncludeAttendance;
    private BigDecimal overtimeRateMultiplier;
    private BigDecimal lateDeductionPerMinute;
    private UUID salaryExpenseAccountId;
    private UUID salaryPayableAccountId;
    private UUID cashBankAccountId;
    private int version;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private String createdBy;
    private String updatedBy;
}
