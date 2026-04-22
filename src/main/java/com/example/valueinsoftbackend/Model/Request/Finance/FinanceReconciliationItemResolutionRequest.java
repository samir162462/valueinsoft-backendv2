package com.example.valueinsoftbackend.Model.Request.Finance;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FinanceReconciliationItemResolutionRequest {
    private int companyId;
    private String resolutionStatus;
    private String resolutionNote;
}
