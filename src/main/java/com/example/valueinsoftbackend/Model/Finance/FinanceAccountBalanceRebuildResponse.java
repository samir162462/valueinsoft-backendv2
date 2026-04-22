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
public class FinanceAccountBalanceRebuildResponse {
    private int companyId;
    private UUID fiscalPeriodId;
    private String currencyCode;
    private int resetRowCount;
    private int branchProjectionRowCount;
    private int companyProjectionRowCount;
    private long totalProjectionRowCount;
    private BigDecimal totalClosingDebit;
    private BigDecimal totalClosingCredit;
    private boolean balanced;
    private Instant rebuiltAt;
}
