package com.example.valueinsoftbackend.Model.Request.Finance;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FinanceReconciliationRunCreateRequest {
    private int companyId;
    private Integer branchId;
    private String reconciliationType;
    private LocalDate periodStart;
    private LocalDate periodEnd;
}
