package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.Model.Configuration.RoleGrantConfig;
import com.example.valueinsoftbackend.Model.Configuration.TenantRoleAssignmentConfig;

import java.util.Objects;

/**
 * Pure scope-resolution rules shared by the trusted enforcement resolver
 * ({@link EffectiveConfigurationService#getEffectiveCapabilities}) and the explainable
 * access-map projection ({@link UserAccessProjectionService}).
 *
 * <p>Extracted verbatim from {@code EffectiveConfigurationService} so both paths cannot
 * drift. Behavior must never change here without updating the resolver contract tests.</p>
 */
public final class CapabilityScopeRules {

    private CapabilityScopeRules() {
    }

    /**
     * A role assignment applies to the active branch context when it is company-scoped,
     * or branch-scoped targeting exactly the active branch.
     */
    public static boolean assignmentAppliesToBranch(TenantRoleAssignmentConfig assignment, Integer activeBranchId) {
        if ("branch".equalsIgnoreCase(assignment.getScopeType())) {
            return activeBranchId != null && Objects.equals(activeBranchId, assignment.getScopeBranchId());
        }
        return true;
    }

    /**
     * A resolved scope applies to the active branch context unless it is branch-scoped
     * for a different (or missing) branch.
     */
    public static boolean scopeAppliesToBranch(String scopeType, Integer scopeBranchId, Integer activeBranchId) {
        if ("branch".equalsIgnoreCase(scopeType)) {
            return activeBranchId != null && Objects.equals(activeBranchId, scopeBranchId);
        }
        return true;
    }

    /**
     * Projects a role grant's scope through the assignment's scope.
     *
     * <ul>
     *   <li>Branch assignment + global_admin grant → dropped (returns null).</li>
     *   <li>Branch assignment + self grant → stays self.</li>
     *   <li>Branch assignment + company/branch grant → narrowed to the assignment branch.</li>
     *   <li>Company assignment → grant keeps its own scope with no concrete branch.</li>
     * </ul>
     */
    public static ResolvedScope resolveScopeForAssignment(RoleGrantConfig roleGrant, TenantRoleAssignmentConfig assignment) {
        if ("branch".equalsIgnoreCase(assignment.getScopeType())) {
            if ("global_admin".equalsIgnoreCase(roleGrant.getScopeType())) {
                return null;
            }
            if ("self".equalsIgnoreCase(roleGrant.getScopeType())) {
                return new ResolvedScope("self", null);
            }
            return new ResolvedScope("branch", assignment.getScopeBranchId());
        }
        return new ResolvedScope(roleGrant.getScopeType(), null);
    }

    /**
     * Canonical map key used by capability resolution: capabilityKey|scopeType|scopeBranchId.
     */
    public static String capabilityMapKey(String capabilityKey, String scopeType, Integer scopeBranchId) {
        return capabilityKey + "|" + scopeType + "|" + (scopeBranchId == null ? "null" : scopeBranchId);
    }

    /**
     * Scope projected for a concrete assignment/grant combination.
     */
    public static final class ResolvedScope {
        private final String scopeType;
        private final Integer scopeBranchId;

        public ResolvedScope(String scopeType, Integer scopeBranchId) {
            this.scopeType = scopeType;
            this.scopeBranchId = scopeBranchId;
        }

        public String getScopeType() {
            return scopeType;
        }

        public Integer getScopeBranchId() {
            return scopeBranchId;
        }
    }
}
