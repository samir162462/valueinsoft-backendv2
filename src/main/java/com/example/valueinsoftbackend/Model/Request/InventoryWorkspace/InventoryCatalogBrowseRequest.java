package com.example.valueinsoftbackend.Model.Request.InventoryWorkspace;

import com.example.valueinsoftbackend.Model.InventoryWorkspace.InventoryCatalogFilters;
import com.example.valueinsoftbackend.Model.InventoryWorkspace.InventorySort;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryCatalogBrowseRequest {
    @NotNull
    private Integer branchId;

    @NotNull
    private Integer companyId;

    private String query;
    private Integer page = 1;
    private Integer pageSize = 25;

    @Valid
    private InventorySort sort = new InventorySort("updatedAt", "desc");

    private ArrayList<String> chips = new ArrayList<>();

    @Valid
    private InventoryCatalogFilters filters = new InventoryCatalogFilters();
}
