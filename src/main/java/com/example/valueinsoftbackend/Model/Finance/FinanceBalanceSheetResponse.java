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
public class FinanceBalanceSheetResponse {
    private int companyId;
    private UUID fiscalPeriodId;
    private Integer branchId;
    private String currencyCode;
    private BigDecimal totalAssets;
    private BigDecimal totalLiabilities;
    private BigDecimal totalEquity;
    private BigDecimal netIncome;
    private BigDecimal totalEquityIncludingNetIncome;
    private BigDecimal liabilitiesAndEquity;
    private BigDecimal balanceDifference;
    private boolean balanced;
    private Instant generatedAt;
    private ArrayList<FinanceStatementLineItem> assetLines;
    private ArrayList<FinanceStatementLineItem> liabilityLines;
    private ArrayList<FinanceStatementLineItem> equityLines;
}
