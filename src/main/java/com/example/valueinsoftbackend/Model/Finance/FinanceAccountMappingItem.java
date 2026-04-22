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
public class FinanceAccountMappingItem {
    private UUID accountMappingId;
    private int companyId;
    private Integer branchId;
    private String mappingKey;
    private UUID accountId;
    private String accountCode;
    private String accountName;
    private int priority;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private String status;
    private int version;
    private Instant createdAt;
    private Integer createdBy;
    private Instant updatedAt;
    private Integer updatedBy;
}
