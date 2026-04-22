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
public class FinancePeriodCloseValidationResponse {
    private int companyId;
    private UUID fiscalPeriodId;
    private String fiscalPeriodName;
    private String fiscalPeriodStatus;
    private String currencyCode;
    private boolean closeAllowed;
    private BigDecimal totalClosingDebit;
    private BigDecimal totalClosingCredit;
    private boolean trialBalanceBalanced;
    private long postedJournalCount;
    private long balanceProjectionRowCount;
    private Instant validatedAt;
    private ArrayList<FinancePeriodCloseValidationCheckItem> checks;
}
