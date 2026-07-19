package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbCompanyTemplates;
import com.example.valueinsoftbackend.DatabaseRequests.DbPackagePlans;
import com.example.valueinsoftbackend.DatabaseRequests.DbPlatformModules;
import com.example.valueinsoftbackend.DatabaseRequests.DbRoleGrants;
import com.example.valueinsoftbackend.DatabaseRequests.DbTenantRoleAssignments;
import com.example.valueinsoftbackend.DatabaseRequests.DbTenantUserGrantOverrides;
import com.example.valueinsoftbackend.DatabaseRequests.DbTenants;
import com.example.valueinsoftbackend.Model.Configuration.AccessMap.AccessMapNode;
import com.example.valueinsoftbackend.Model.Configuration.AccessMap.UserAccessMapResponse;
import com.example.valueinsoftbackend.Model.Configuration.ConfigurationAdminUserSummary;
import com.example.valueinsoftbackend.Model.Configuration.EffectiveModuleConfig;
import com.example.valueinsoftbackend.Model.Configuration.PlatformCapabilityConfig;
import com.example.valueinsoftbackend.Model.Configuration.PlatformModuleConfig;
import com.example.valueinsoftbackend.Model.Configuration.ResolvedCapabilityConfig;
import com.example.valueinsoftbackend.Model.Configuration.RoleGrantConfig;
import com.example.valueinsoftbackend.Model.Configuration.TenantConfig;
import com.example.valueinsoftbackend.Model.Configuration.TenantRoleAssignmentConfig;
import com.example.valueinsoftbackend.Model.Configuration.TenantUserGrantOverrideConfig;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * Drift guard: for every capability, the explainable projection must agree with the
 * trusted enforcement resolver ({@link EffectiveConfigurationService#getEffectiveCapabilities})
 * about whether the user effectively holds an allow.
 */
class UserAccessProjectionConsistencyTest {

    private static final int TENANT_ID = 12;
    private static final int USER_ID = 41;
    private static final Integer BRANCH_ID = 3;

    @Test
    void projectionAgreesWithTrustedResolverOnEffectiveAccess() {
        // ---- Fixture matrix covering the resolver's interesting paths. ----
        List<PlatformCapabilityConfig> catalog = List.of(
                cap("inventory.item.read", "branch"),      // role-granted via branch assignment
                cap("inventory.item.edit", "branch"),      // denied after role grant
                cap("inventory.item.create", "branch"),    // direct allow override
                cap("inventory.item.export", "branch"),    // inert: company assignment of branch capability
                cap("inventory.item.delete", "branch"),    // grant for another branch only
                cap("finance.report.read", "company"),     // role-granted company-wide
                cap("finance.report.edit", "company"),     // not granted at all
                cap("profile.account.read", "self")        // self scope via branch assignment
        );

        List<TenantRoleAssignmentConfig> assignments = List.of(
                assignment(1L, "ClerkBranch", "branch", BRANCH_ID),
                assignment(2L, "ClerkCompany", "company", null),
                assignment(3L, "OtherBranchRole", "branch", 99)
        );

        List<RoleGrantConfig> grants = List.of(
                grant("ClerkBranch", "inventory.item.read", "branch"),
                grant("ClerkBranch", "inventory.item.edit", "branch"),
                grant("ClerkBranch", "profile.account.read", "self"),
                grant("ClerkCompany", "inventory.item.export", "branch"),   // inert combination
                grant("ClerkCompany", "finance.report.read", "company"),
                grant("OtherBranchRole", "inventory.item.delete", "branch") // other branch only
        );

        List<TenantUserGrantOverrideConfig> overrides = List.of(
                override(1L, "inventory.item.edit", "deny", "branch", BRANCH_ID),
                override(2L, "inventory.item.create", "allow", "branch", BRANCH_ID)
        );

        // ---- Trusted resolver via EffectiveConfigurationService with mocked repositories. ----
        DbTenants dbTenants = Mockito.mock(DbTenants.class);
        DbTenantRoleAssignments dbAssignments = Mockito.mock(DbTenantRoleAssignments.class);
        DbTenantUserGrantOverrides dbOverrides = Mockito.mock(DbTenantUserGrantOverrides.class);
        DbRoleGrants dbRoleGrants = Mockito.mock(DbRoleGrants.class);
        TenantConfig tenant = Mockito.mock(TenantConfig.class);
        when(tenant.getTenantId()).thenReturn(TENANT_ID);
        when(dbTenants.getTenantById(TENANT_ID)).thenReturn(tenant);
        when(dbAssignments.getUserTenantRoleAssignments(anyInt(), anyInt())).thenReturn(assignments);
        when(dbOverrides.getUserGrantOverrides(anyInt(), anyInt())).thenReturn(overrides);
        when(dbRoleGrants.getGrantsForRoleIds(anyList())).thenReturn(grants);

        EffectiveConfigurationService trusted = new EffectiveConfigurationService(
                Mockito.mock(DbPlatformModules.class),
                Mockito.mock(DbPackagePlans.class),
                Mockito.mock(DbCompanyTemplates.class),
                dbTenants,
                dbAssignments,
                dbOverrides,
                dbRoleGrants
        );

        ArrayList<ResolvedCapabilityConfig> resolved = trusted.getEffectiveCapabilities(TENANT_ID, USER_ID, BRANCH_ID);
        Set<String> trustedAllows = new HashSet<>();
        for (ResolvedCapabilityConfig capability : resolved) {
            if ("allow".equalsIgnoreCase(capability.getGrantMode())) {
                trustedAllows.add(capability.getCapabilityKey());
            }
        }

        // ---- Explainable projection over the same fixtures. ----
        UserAccessMapResponse response = new UserAccessProjectionService().buildAccessMap(
                new UserAccessProjectionService.ProjectionInput(
                        new ConfigurationAdminUserSummary(USER_ID, "ahmed", "a@x.com", "Ahmed", "Mohamed",
                                "0100", "Manager", BRANCH_ID, "Cairo", null),
                        TENANT_ID, BRANCH_ID, true,
                        catalog,
                        List.of(module("inventory"), module("finance"), module("profile")),
                        List.of(effective("inventory"), effective("finance"), effective("profile")),
                        assignments, grants, overrides,
                        List.of()
                ));

        Set<String> projectedAllows = new HashSet<>();
        for (AccessMapNode node : response.getNodes()) {
            if ("CAPABILITY".equals(node.getType()) && Boolean.TRUE.equals(node.getEffectiveAccess())) {
                projectedAllows.add(node.getCapabilityKey());
            }
        }

        assertEquals(trustedAllows, projectedAllows,
                "Projection and trusted resolver disagree on effective access");

        // Sanity: the matrix exercises both granted and non-granted outcomes.
        assertEquals(Set.of("inventory.item.read", "inventory.item.create",
                "finance.report.read", "profile.account.read"), projectedAllows);
    }

    private static PlatformCapabilityConfig cap(String key, String scope) {
        String[] parts = key.split("\\.");
        return new PlatformCapabilityConfig(key, parts[0], parts[1], parts[2], scope, "active", "desc");
    }

    private static PlatformModuleConfig module(String moduleId) {
        return new PlatformModuleConfig(moduleId, moduleId, "category", "active", true, "v1", "desc");
    }

    private static EffectiveModuleConfig effective(String moduleId) {
        return new EffectiveModuleConfig(moduleId, moduleId, "category", true, "package", "standard");
    }

    private static TenantRoleAssignmentConfig assignment(long id, String roleId, String scopeType, Integer branchId) {
        return new TenantRoleAssignmentConfig(id, TENANT_ID, USER_ID, roleId, "active", null, null, "admin", scopeType, branchId);
    }

    private static RoleGrantConfig grant(String roleId, String capabilityKey, String scopeType) {
        return new RoleGrantConfig(roleId, capabilityKey, scopeType, "allow", "v1");
    }

    private static TenantUserGrantOverrideConfig override(long id, String capabilityKey, String grantMode,
                                                          String scopeType, Integer branchId) {
        return new TenantUserGrantOverrideConfig(id, TENANT_ID, USER_ID, capabilityKey, grantMode, scopeType, branchId, "reason", "admin");
    }
}
