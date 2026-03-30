package com.example.valueinsoftbackend.Model.Configuration;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TenantModuleOverrideConfig {
    private int tenantId;
    private String moduleId;
    private boolean enabled;
    private String mode;
    private String reason;
    private String source;
    private String version;
}
