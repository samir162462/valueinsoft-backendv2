package com.example.valueinsoftbackend.Model.Finance;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FinanceGeneralLedgerLineItem {
    private UUID journalLineId;
    private UUID journalEntryId;
    private String journalNumber;
    private String journalType;
    private String journalStatus;
    private int lineNumber;
    private LocalDate postingDate;
    private UUID fiscalPeriodId;
    private UUID accountId;
    private String accountCode;
    private String accountName;
    private Integer branchId;
    private BigDecimal debitAmount;
    private BigDecimal creditAmount;
    private BigDecimal runningBalance;
    private String currencyCode;
    private String description;
    private String sourceModule;
    private String sourceType;
    private String sourceId;
    private UUID costCenterId;
    private String costCenterCode;
    private String costCenterName;
    private UUID taxCodeId;
    private String taxCode;
}
