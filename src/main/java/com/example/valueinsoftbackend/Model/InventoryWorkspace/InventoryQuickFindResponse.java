package com.example.valueinsoftbackend.Model.InventoryWorkspace;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryQuickFindResponse {
    private String mode;
    private String query;
    private String resolvedType;
    private String matchedBy;
    private InventoryExactMatchResult exactMatch;
    private ArrayList<InventoryCatalogItem> fallbackMatches = new ArrayList<>();
    private InventorySummaryResponse summary;
    private String emptyReason;
}
