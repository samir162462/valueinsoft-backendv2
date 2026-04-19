package com.example.valueinsoftbackend.Model.InventoryWorkspace;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryCatalogBrowseResponse {
    private String mode;
    private String query;
    private Integer page;
    private Integer pageSize;
    private InventorySort sort;
    private ArrayList<InventoryCatalogItem> data = new ArrayList<>();
    private InventoryPagination pagination;
    private InventorySummaryResponse summary;
    private LinkedHashMap<String, Integer> chipCounts = new LinkedHashMap<>();
}
