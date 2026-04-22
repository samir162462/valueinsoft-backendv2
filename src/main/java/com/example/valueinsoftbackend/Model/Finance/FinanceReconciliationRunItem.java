package com.example.valueinsoftbackend.Model.Finance;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FinanceReconciliationRunItem {
    private UUID reconciliationRunId;
    private int companyId;
    private Integer branchId;
    private String reconciliationType;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private String status;
    private BigDecimal differenceAmount;
    private Integer startedBy;
    private Instant startedAt;
    private Instant completedAt;
    private Instant createdAt;
    private Integer createdBy;
    private Instant updatedAt;
    private Integer updatedBy;
}
