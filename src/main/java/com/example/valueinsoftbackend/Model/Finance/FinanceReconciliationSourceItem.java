package com.example.valueinsoftbackend.Model.Finance;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FinanceReconciliationSourceItem {
    private UUID reconciliationSourceItemId;
    private int companyId;
    private Integer branchId;
    private String reconciliationType;
    private String sourceSystem;
    private String externalReference;
    private LocalDate sourceDate;
    private BigDecimal amount;
    private String currencyCode;
    private String description;
    private String rawPayloadJson;
    private String status;
    private Instant createdAt;
    private Integer createdBy;
    private Instant updatedAt;
    private Integer updatedBy;
}
