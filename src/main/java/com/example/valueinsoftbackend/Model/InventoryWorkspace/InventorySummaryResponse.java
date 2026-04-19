package com.example.valueinsoftbackend.Model.InventoryWorkspace;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventorySummaryResponse {
    private Long resultCount;
    private Long inStockCount;
    private Long outOfStockCount;
    private Long lowStockCount;
    private Long usedCount;
    private Long sellableCount;
    private Long movementCount;
    private Long itemsAdded;
    private Long itemsSold;
    private Long itemsReturned;
    private Long itemsAdjusted;
    private Long netQuantityChange;
    private Long distinctProducts;
    private Long totalCandidates;
}
