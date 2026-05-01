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
public class PayrollRun {
    private long id;
    private int companyId;
    private Integer branchId;
    private String runLabel;
    private Date periodStart;
    private Date periodEnd;
    private String frequency;
    private String currencyCode;
    private String status;            // DRAFT | CALCULATED | APPROVED | POSTING_IN_PROGRESS | POSTED | PARTIALLY_PAID | PAID | CANCELLED | FAILED_POSTING | REVERSED
    private BigDecimal totalGross;
    private BigDecimal totalDeductions;
    private BigDecimal totalNet;
    private int employeeCount;
    private String approvedBy;
    private Timestamp approvedAt;
    private UUID postingRequestId;
    private UUID postedJournalId;
    private Timestamp postedAt;
    private int version;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private String createdBy;
    private String updatedBy;
}
