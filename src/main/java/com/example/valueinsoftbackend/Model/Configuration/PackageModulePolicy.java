package com.example.valueinsoftbackend.Model.Configuration;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PackageModulePolicy {
    private String packageId;
    private String moduleId;
    private boolean enabled;
    private String mode;
    private String limitsJson;
    private String policyVersion;
}
