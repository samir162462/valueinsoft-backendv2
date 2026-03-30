package com.example.valueinsoftbackend.Model.Configuration;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TenantUserGrantOverrideConfig {
    private long overrideId;
    private int tenantId;
    private int userId;
    private String capabilityKey;
    private String grantMode;
    private String scopeType;
    private Integer scopeBranchId;
    private String reason;
    private String source;
}
