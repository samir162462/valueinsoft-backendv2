package com.example.valueinsoftbackend.Model.Request.InventoryWorkspace;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

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
