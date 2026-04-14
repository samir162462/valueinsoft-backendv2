package com.example.valueinsoftbackend.Model.BranchSettings;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;
import java.util.ArrayList;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BranchSettingsBundleResponse {
    private Integer tenantId;
    private Integer branchId;
    private ArrayList<BranchSettingDefinitionConfig> definitions;
    private ArrayList<BranchEffectiveSettingConfig> effectiveSettings;
    private Timestamp loadedAt;
    private String source;
}
