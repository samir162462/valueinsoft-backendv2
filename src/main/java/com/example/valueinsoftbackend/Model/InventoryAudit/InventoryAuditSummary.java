package com.example.valueinsoftbackend.Model.InventoryAudit;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryAuditSummary {
    private long totalRows;
    private long totalOpeningQty;
    private long totalInQty;
    private long totalOutQty;
    private long totalClosingQty;
    private long totalStockValue;
    private long lowStockCount;
}
