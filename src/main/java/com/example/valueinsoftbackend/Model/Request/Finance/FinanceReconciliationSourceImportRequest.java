package com.example.valueinsoftbackend.Model.Request.Finance;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FinanceReconciliationSourceImportRequest {
    private int companyId;
    private Integer branchId;
    private String reconciliationType;
    private String sourceSystem;
    private ArrayList<FinanceReconciliationSourceImportItemRequest> items;
}
