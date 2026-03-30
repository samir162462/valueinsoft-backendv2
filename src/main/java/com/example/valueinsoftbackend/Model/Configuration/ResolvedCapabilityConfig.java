package com.example.valueinsoftbackend.Model.Configuration;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a capability that is effective for the current user and tenant context
 * after role grants and user-specific overrides are resolved.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResolvedCapabilityConfig {
    private String capabilityKey;
    private String grantMode;
    private String scopeType;
    private Integer scopeBranchId;
    private String source;
    private String roleId;
    private Long roleAssignmentId;
    private Long userOverrideId;
}
