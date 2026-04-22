package com.example.valueinsoftbackend.Model.Finance;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FinanceClosingEntryResponse {
    private int companyId;
    private UUID fiscalPeriodId;
    private UUID journalEntryId;
    private String journalNumber;
    private String status;
    private String currencyCode;
    private BigDecimal totalDebit;
    private BigDecimal totalCredit;
    private BigDecimal netIncome;
    private int closedRevenueExpenseLineCount;
    private UUID retainedEarningsAccountId;
    private String retainedEarningsAccountCode;
    private Instant generatedAt;
    private String correlationId;
}
