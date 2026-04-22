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
public class FinanceJournalEntryItem {
    private UUID journalEntryId;
    private int companyId;
    private Integer branchId;
    private String journalNumber;
    private String journalType;
    private String sourceModule;
    private String sourceType;
    private String sourceId;
    private LocalDate postingDate;
    private UUID fiscalPeriodId;
    private String fiscalPeriodName;
    private String description;
    private String status;
    private String currencyCode;
    private BigDecimal exchangeRate;
    private BigDecimal totalDebit;
    private BigDecimal totalCredit;
    private boolean closingEntry;
    private Instant postedAt;
    private Integer postedBy;
    private UUID reversalOfJournalId;
    private UUID reversedByJournalId;
    private UUID postingBatchId;
    private int version;
    private Instant createdAt;
    private Integer createdBy;
    private Instant updatedAt;
    private Integer updatedBy;
}
