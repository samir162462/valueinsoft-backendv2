package com.example.valueinsoftbackend.Model.Finance;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FinanceTrialBalanceResponse {
    private int companyId;
    private UUID fiscalPeriodId;
    private Integer branchId;
    private String currencyCode;
    private boolean includeZeroBalances;
    private BigDecimal totalOpeningDebit;
    private BigDecimal totalOpeningCredit;
    private BigDecimal totalPeriodDebit;
    private BigDecimal totalPeriodCredit;
    private BigDecimal totalClosingDebit;
    private BigDecimal totalClosingCredit;
    private boolean balanced;
    private Instant generatedAt;
    private ArrayList<FinanceTrialBalanceLineItem> lines;
}
