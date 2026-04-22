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
public class FinanceGeneralLedgerResponse {
    private int companyId;
    private UUID fiscalPeriodId;
    private UUID accountId;
    private String accountCode;
    private String accountName;
    private String normalBalance;
    private Integer branchId;
    private String currencyCode;
    private BigDecimal openingDebit;
    private BigDecimal openingCredit;
    private BigDecimal periodDebit;
    private BigDecimal periodCredit;
    private BigDecimal closingDebit;
    private BigDecimal closingCredit;
    private BigDecimal openingNormalBalance;
    private BigDecimal closingNormalBalance;
    private int limit;
    private int offset;
    private Instant generatedAt;
    private ArrayList<FinanceGeneralLedgerLineItem> lines;
}
