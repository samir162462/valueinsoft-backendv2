package com.example.valueinsoftbackend.Model.Request.InventoryWorkspace;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryPresetUpdateRequest {
    private String name;
    private String scope;
    private String mode;
    private Integer branchId;
    private String roleTarget;
    private LinkedHashMap<String, Object> queryState = new LinkedHashMap<>();
}
