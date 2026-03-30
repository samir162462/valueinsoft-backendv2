package com.example.valueinsoftbackend.Model.Configuration;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoleGrantConfig {
    private String roleId;
    private String capabilityKey;
    private String scopeType;
    private String grantMode;
    private String grantVersion;
}
