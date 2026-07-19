package com.example.valueinsoftbackend.Model.Configuration.AccessMap;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Reference to a role assignment that contributes (or would contribute) access.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccessMapRoleRef {
    private String roleId;
    private String displayName;
    private Long assignmentId;
    private String assignmentScopeType;
    private Integer assignmentScopeBranchId;
}
