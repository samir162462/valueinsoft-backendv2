package com.example.valueinsoftbackend.Model.InventoryWorkspace;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryPresetResponse {
    private String presetId;
    private String name;
    private String scope;
    private String mode;
    private Integer branchId;
    private String roleTarget;
    private String createdBy;
    private Boolean canManage;
    private LinkedHashMap<String, Object> queryState = new LinkedHashMap<>();
}
