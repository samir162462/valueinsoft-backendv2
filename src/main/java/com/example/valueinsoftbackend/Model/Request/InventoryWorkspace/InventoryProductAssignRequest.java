package com.example.valueinsoftbackend.Model.Request.InventoryWorkspace;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class InventoryProductAssignRequest {
    @NotNull(message = "companyId is required")
    private Integer companyId;

    @NotNull(message = "branchId is required")
    private Integer branchId;

    @NotNull(message = "productId is required")
    private Long productId;
    
    private Integer defaultSupplierId;
}
