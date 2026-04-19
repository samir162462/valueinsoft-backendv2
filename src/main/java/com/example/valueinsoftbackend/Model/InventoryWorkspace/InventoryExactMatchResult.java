package com.example.valueinsoftbackend.Model.InventoryWorkspace;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryExactMatchResult {
    private Boolean found;
    private String matchedBy;
    private InventoryCatalogItem product;
}
