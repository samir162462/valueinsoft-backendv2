package com.example.valueinsoftbackend.Model.BranchSettings;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BranchEffectiveSettingConfig {
    private String settingKey;
    private Object effectiveValue;
    private String source;
}
