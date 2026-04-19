package com.example.valueinsoftbackend.Model.InventoryWorkspace;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryAnalysisResponse {
    private String mode;
    private InventorySummaryResponse summary;
    private ArrayList<InventoryKpiItem> kpis = new ArrayList<>();
    private LinkedHashMap<String, Integer> movementCounts = new LinkedHashMap<>();
    private ArrayList<InventoryMovementItem> data = new ArrayList<>();
    private InventoryPagination pagination;
}
