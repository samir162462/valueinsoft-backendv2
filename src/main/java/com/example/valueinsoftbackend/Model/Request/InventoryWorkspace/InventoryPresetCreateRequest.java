package com.example.valueinsoftbackend.Model.Request.InventoryWorkspace;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.LinkedHashMap;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryPresetCreateRequest {
    @NotBlank
    private String name;

    @NotBlank
    private String scope;

    @NotBlank
    private String mode;

    private Integer branchId;
    private String roleTarget;

    @NotNull
    private LinkedHashMap<String, Object> queryState = new LinkedHashMap<>();
}
