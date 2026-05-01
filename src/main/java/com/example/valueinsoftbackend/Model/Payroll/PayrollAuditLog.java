package com.example.valueinsoftbackend.Model.Payroll;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayrollAuditLog {
    private long id;
    private int companyId;
    private Integer branchId;
    private String entityType;       // salary_profile | adjustment | payroll_run | payment
    private String entityId;
    private String action;           // e.g. PROFILE_CREATED, RUN_APPROVED, RUN_POSTED, etc.
    private String oldValueJson;
    private String newValueJson;
    private String performedBy;
    private Timestamp performedAt;
    private String remarks;
}
