package com.example.valueinsoftbackend.Model.PlatformAdmin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformBillingReconciliationRepairResponse {
    private Integer tenantId;
    private int checkedItems;
    private int repairedItems;
    private int skippedItems;
    private Timestamp generatedAt;
}
