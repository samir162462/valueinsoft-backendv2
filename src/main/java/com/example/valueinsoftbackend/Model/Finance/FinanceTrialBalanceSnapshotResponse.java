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
public class FinanceTrialBalanceSnapshotResponse {
    private int companyId;
    private UUID fiscalPeriodId;
    private UUID trialBalanceSnapshotId;
    private String snapshotType;
    private boolean includesClosingEntries;
    private String currencyCode;
    private BigDecimal totalDebit;
    private BigDecimal totalCredit;
    private boolean balanced;
    private long balanceRowCount;
    private Instant generatedAt;
    private String correlationId;
}
