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
public class FinanceProfitAndLossResponse {
    private int companyId;
    private UUID fiscalPeriodId;
    private Integer branchId;
    private String currencyCode;
    private BigDecimal totalRevenue;
    private BigDecimal totalExpenses;
    private BigDecimal netIncome;
    private Instant generatedAt;
    private ArrayList<FinanceStatementLineItem> revenueLines;
    private ArrayList<FinanceStatementLineItem> expenseLines;
}
