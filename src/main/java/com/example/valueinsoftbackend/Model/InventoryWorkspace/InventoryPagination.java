package com.example.valueinsoftbackend.Model.InventoryWorkspace;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryPagination {
    private Integer page;
    private Integer pageSize;
    private Long totalRows;
    private Integer totalPages;
    private Boolean hasNextPage;
}
