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
public class FinancePostingRequestItem {
    private UUID postingRequestId;
    private int companyId;
    private Integer branchId;
    private UUID postingBatchId;
    private String sourceModule;
    private String sourceType;
    private String sourceId;
    private LocalDate postingDate;
    private UUID fiscalPeriodId;
    private String requestHash;
    private String requestPayloadJson;
    private String status;
    private int attemptCount;
    private Instant lastAttemptAt;
    private String lastError;
    private UUID journalEntryId;
    private Instant createdAt;
    private Integer createdBy;
    private Instant updatedAt;
    private Integer updatedBy;
}
