package com.example.valueinsoftbackend.Model.Configuration;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TenantWorkflowOverrideConfig {
    private int tenantId;
    private String flagKey;
    private String flagValueJson;
    private String reason;
    private String source;
    private String version;
}
