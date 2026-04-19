package com.example.valueinsoftbackend.Model.Request.InventoryWorkspace;

import com.example.valueinsoftbackend.Model.InventoryWorkspace.InventoryAnalysisFilters;
import com.example.valueinsoftbackend.Model.InventoryWorkspace.InventoryDateRange;
import com.example.valueinsoftbackend.Model.InventoryWorkspace.InventorySort;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


import java.util.ArrayList;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryAnalysisRequest {
    @NotNull
    private Integer branchId;

    @NotNull
    private Integer companyId;

    @Valid
    @NotNull
    private InventoryDateRange dateRange = new InventoryDateRange();

    private ArrayList<String> movementTypes = new ArrayList<>();
    private String query;

    @Valid
    private InventoryAnalysisFilters filters = new InventoryAnalysisFilters();

    private Integer page = 1;
    private Integer pageSize = 50;

    @Valid
    private InventorySort sort = new InventorySort("movementAt", "desc");
}
