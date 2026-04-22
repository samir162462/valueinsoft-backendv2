package com.example.valueinsoftbackend.Model.Finance;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FinancePeriodCloseRunResponse {
    private int companyId;
    private UUID fiscalPeriodId;
    private UUID periodCloseRunId;
    private UUID trialBalanceSnapshotId;
    private String status;
    private String periodStatus;
    private Instant completedAt;
    private String correlationId;
}
