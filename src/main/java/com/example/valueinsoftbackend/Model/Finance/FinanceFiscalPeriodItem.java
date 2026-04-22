package com.example.valueinsoftbackend.Model.Finance;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FinanceFiscalPeriodItem {
    private UUID fiscalPeriodId;
    private int companyId;
    private UUID fiscalYearId;
    private int periodNumber;
    private String name;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private Instant lockedAt;
    private Integer lockedBy;
    private Instant closedAt;
    private Integer closedBy;
    private int version;
    private Instant createdAt;
    private Integer createdBy;
    private Instant updatedAt;
    private Integer updatedBy;
}
