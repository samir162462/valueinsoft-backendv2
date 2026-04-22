package com.example.valueinsoftbackend.Model.Finance;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FinanceReconciliationSourceImportResponse {
    private int companyId;
    private Integer branchId;
    private String reconciliationType;
    private String sourceSystem;
    private int importedCount;
    private Instant importedAt;
    private ArrayList<FinanceReconciliationSourceItem> items;
}
