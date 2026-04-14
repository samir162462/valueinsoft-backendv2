package com.example.valueinsoftbackend.Model.BranchSettings;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BranchSettingDefinitionConfig {
    private String groupKey;
    private String settingKey;
    private String displayName;
    private String description;
    private String valueType;
    private String fieldType;
    private Object defaultValue;
    private Object options;
    private Object validation;
    private boolean active;
    private int sortOrder;
}
