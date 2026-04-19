package com.example.valueinsoftbackend.Model.InventoryWorkspace;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryAnalysisFilters {
    private String businessLineKey;
    private String templateKey;
    private Integer supplierId;
    private Integer productId;
    private ArrayList<String> stockImpact = new ArrayList<>();
}
