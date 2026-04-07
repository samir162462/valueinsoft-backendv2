package com.example.valueinsoftbackend.Model.PlatformAdmin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformPlanModuleItem {
    private String packageId;
    private String moduleId;
    private String displayName;
    private String category;
    private boolean enabled;
    private String mode;
    private String limitsJson;
    private String policyVersion;
}
