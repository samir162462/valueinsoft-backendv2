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
public class FinanceReconciliationItemItem {
    private UUID reconciliationItemId;
    private int companyId;
    private UUID reconciliationRunId;
    private UUID reconciliationSourceItemId;
    private String sourceType;
    private String sourceId;
    private UUID ledgerLineId;
    private String matchStatus;
    private BigDecimal differenceAmount;
    private String resolutionStatus;
    private String resolutionNote;
    private String resolutionProofFileKey;
    private String resolutionProofFileName;
    private String resolutionProofFileType;
    private Long resolutionProofFileSize;
    private Instant resolutionProofUploadedAt;
    private Integer resolutionProofUploadedBy;
    private Instant createdAt;
    private Integer createdBy;
    private Instant updatedAt;
    private Integer updatedBy;
}
