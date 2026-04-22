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
public class FinanceJournalLineItem {
    private UUID journalLineId;
    private int companyId;
    private UUID journalEntryId;
    private int lineNumber;
    private UUID accountId;
    private String accountCode;
    private String accountName;
    private Integer branchId;
    private LocalDate postingDate;
    private UUID fiscalPeriodId;
    private BigDecimal debitAmount;
    private BigDecimal creditAmount;
    private String currencyCode;
    private BigDecimal exchangeRate;
    private BigDecimal foreignDebitAmount;
    private BigDecimal foreignCreditAmount;
    private String description;
    private Integer customerId;
    private Integer supplierId;
    private Long productId;
    private Long inventoryMovementId;
    private String paymentId;
    private UUID costCenterId;
    private String costCenterCode;
    private String costCenterName;
    private UUID taxCodeId;
    private String taxCode;
    private String sourceModule;
    private String sourceType;
    private String sourceId;
    private Instant createdAt;
    private Integer createdBy;
}
