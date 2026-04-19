package com.example.valueinsoftbackend.Model.Request.InventoryWorkspace;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;



@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryQuickFindRequest {
    @NotNull
    private Integer branchId;

    @NotNull
    private Integer companyId;

    @NotBlank
    private String query;

    private String exactType = "auto";
    private Integer limit = 10;
}
