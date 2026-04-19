package com.example.valueinsoftbackend.Model.InventoryAudit;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryAuditGroupSummary {
    private String groupKey;
    private long rowCount;
    private long totalClosingQty;
    private long totalValue;
}
