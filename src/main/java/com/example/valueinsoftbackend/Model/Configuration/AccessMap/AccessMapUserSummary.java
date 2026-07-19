package com.example.valueinsoftbackend.Model.Configuration.AccessMap;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;

/**
 * Target-user header information for the access map. Contains no data beyond
 * what the tenant admin portal already exposes for tenant members.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccessMapUserSummary {
    private int userId;
    private String userName;
    private String displayName;
    private String email;
    private Integer branchId;
    private String branchName;
    private String legacyRole;
    /**
     * True when the target user is a legacy Owner principal: the enforcement layer
     * bypasses capability checks for owners inside their own tenant, so per-capability
     * states understate this user's real access.
     */
    private boolean ownerBypass;
    private ArrayList<AccessMapRoleRef> assignedRoles;
}
