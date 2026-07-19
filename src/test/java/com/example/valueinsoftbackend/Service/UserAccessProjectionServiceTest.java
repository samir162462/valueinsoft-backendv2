package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.Model.Configuration.AccessMap.AccessMapNode;
import com.example.valueinsoftbackend.Model.Configuration.AccessMap.UserAccessMapResponse;
import com.example.valueinsoftbackend.Model.Configuration.ConfigurationAdminUserSummary;
import com.example.valueinsoftbackend.Model.Configuration.EffectiveModuleConfig;
import com.example.valueinsoftbackend.Model.Configuration.PlatformCapabilityConfig;
import com.example.valueinsoftbackend.Model.Configuration.PlatformModuleConfig;
import com.example.valueinsoftbackend.Model.Configuration.RoleDefinitionConfig;
import com.example.valueinsoftbackend.Model.Configuration.RoleGrantConfig;
import com.example.valueinsoftbackend.Model.Configuration.TenantRoleAssignmentConfig;
import com.example.valueinsoftbackend.Model.Configuration.TenantUserGrantOverrideConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserAccessProjectionServiceTest {

    private static final int TENANT_ID = 12;
    private static final int USER_ID = 41;
    private static final int BRANCH_ID = 3;

    private UserAccessProjectionService service;

    private List<PlatformCapabilityConfig> capabilities;
    private List<PlatformModuleConfig> platformModules;
    private List<EffectiveModuleConfig> effectiveModules;
    private List<TenantRoleAssignmentConfig> assignments;
    private List<RoleGrantConfig> grants;
    private List<TenantUserGrantOverrideConfig> overrides;
    private List<RoleDefinitionConfig> roleDefinitions;
    private ConfigurationAdminUserSummary targetUser;
    private boolean canMutate;

    @BeforeEach
    void setUp() {
        service = new UserAccessProjectionService();
        capabilities = new ArrayList<>();
        platformModules = new ArrayList<>();
        effectiveModules = new ArrayList<>();
        assignments = new ArrayList<>();
        grants = new ArrayList<>();
        overrides = new ArrayList<>();
        roleDefinitions = new ArrayList<>();
        targetUser = user("ahmed", "Ahmed", "Mohamed", "Manager");
        canMutate = true;

        platformModules.add(new PlatformModuleConfig("inventory", "Inventory", "operations", "active", true, "v1", "Inventory module"));
        effectiveModules.add(new EffectiveModuleConfig("inventory", "Inventory", "operations", true, "package", "standard"));
        roleDefinitions.add(new RoleDefinitionConfig("InventoryClerk", "Inventory Clerk", "tenant", "active", "Clerk"));
        roleDefinitions.add(new RoleDefinitionConfig("BranchManager", "Branch Manager", "tenant", "active", "Manager"));
    }

    // ---------- helpers ----------

    private ConfigurationAdminUserSummary user(String userName, String first, String last, String legacyRole) {
        return new ConfigurationAdminUserSummary(USER_ID, userName, userName + "@x.com", first, last, "0100", legacyRole, BRANCH_ID, "Cairo Branch", null);
    }

    private PlatformCapabilityConfig capability(String key, String module, String resource, String action, String scope, String status) {
        return new PlatformCapabilityConfig(key, module, resource, action, scope, status, "desc " + key);
    }

    private TenantRoleAssignmentConfig assignment(long id, String roleId, String scopeType, Integer scopeBranchId) {
        return new TenantRoleAssignmentConfig(id, TENANT_ID, USER_ID, roleId, "active", null, null, "admin", scopeType, scopeBranchId);
    }

    private RoleGrantConfig grant(String roleId, String capabilityKey, String scopeType) {
        return new RoleGrantConfig(roleId, capabilityKey, scopeType, "allow", "v1");
    }

    private TenantUserGrantOverrideConfig override(long id, String capabilityKey, String grantMode, String scopeType, Integer scopeBranchId) {
        return new TenantUserGrantOverrideConfig(id, TENANT_ID, USER_ID, capabilityKey, grantMode, scopeType, scopeBranchId, "test_reason", "admin");
    }

    private UserAccessMapResponse project() {
        return service.buildAccessMap(new UserAccessProjectionService.ProjectionInput(
                targetUser, TENANT_ID, BRANCH_ID, canMutate,
                capabilities, platformModules, effectiveModules,
                assignments, grants, overrides, roleDefinitions));
    }

    private AccessMapNode capabilityNode(UserAccessMapResponse response, String capabilityKey) {
        for (AccessMapNode node : response.getNodes()) {
            if ("CAPABILITY".equals(node.getType()) && capabilityKey.equals(node.getCapabilityKey())) {
                return node;
            }
        }
        throw new AssertionError("Capability node not found: " + capabilityKey);
    }

    private AccessMapNode node(UserAccessMapResponse response, String id) {
        for (AccessMapNode node : response.getNodes()) {
            if (id.equals(node.getId())) {
                return node;
            }
        }
        throw new AssertionError("Node not found: " + id);
    }

    // ---------- states ----------

    @Test
    void roleGrantedCapabilityThroughCompanyAssignment() {
        capabilities.add(capability("inventory.item.read", "inventory", "item", "read", "company", "active"));
        assignments.add(assignment(1L, "InventoryClerk", "company", null));
        grants.add(grant("InventoryClerk", "inventory.item.read", "company"));

        AccessMapNode node = capabilityNode(project(), "inventory.item.read");
        assertEquals(AccessMapNode.STATE_ROLE_GRANTED, node.getAccessState());
        assertTrue(node.getUsableAccess());
        assertEquals("role_grant", node.getSource());
        assertEquals(1, node.getGrantingRoles().size());
        assertEquals("Inventory Clerk", node.getGrantingRoles().get(0).getDisplayName());
        assertTrue(node.getCanBlock());
        assertFalse(node.getCanGrant());
    }

    @Test
    void directAllowOverrideIsDirectGranted() {
        capabilities.add(capability("inventory.item.create", "inventory", "item", "create", "branch", "active"));
        overrides.add(override(881L, "inventory.item.create", "allow", "branch", BRANCH_ID));

        AccessMapNode node = capabilityNode(project(), "inventory.item.create");
        assertEquals(AccessMapNode.STATE_DIRECT_GRANTED, node.getAccessState());
        assertEquals("user_override", node.getSource());
        assertEquals(881L, node.getAllowOverrideId());
        assertEquals("test_reason", node.getOverrideReason());
        assertTrue(node.getCanRemoveDirectGrant());
        assertFalse(node.getCanBlock());
    }

    @Test
    void standaloneDenyIsExplicitlyDenied() {
        capabilities.add(capability("inventory.item.delete", "inventory", "item", "delete", "branch", "active"));
        overrides.add(override(900L, "inventory.item.delete", "deny", "branch", BRANCH_ID));

        AccessMapNode node = capabilityNode(project(), "inventory.item.delete");
        assertEquals(AccessMapNode.STATE_EXPLICITLY_DENIED, node.getAccessState());
        assertFalse(node.getEffectiveAccess());
        assertEquals(900L, node.getDenyOverrideId());
        assertTrue(node.getBlockedRoles().isEmpty());
        assertTrue(node.getCanRestoreRoleAccess());
    }

    @Test
    void denySuppressingRoleGrantRecordsBlockedRoles() {
        capabilities.add(capability("inventory.item.read", "inventory", "item", "read", "branch", "active"));
        assignments.add(assignment(1L, "InventoryClerk", "branch", BRANCH_ID));
        grants.add(grant("InventoryClerk", "inventory.item.read", "branch"));
        overrides.add(override(901L, "inventory.item.read", "deny", "branch", BRANCH_ID));

        AccessMapNode node = capabilityNode(project(), "inventory.item.read");
        assertEquals(AccessMapNode.STATE_EXPLICITLY_DENIED, node.getAccessState());
        assertEquals(1, node.getBlockedRoles().size());
        assertEquals("InventoryClerk", node.getBlockedRoles().get(0).getRoleId());
        assertTrue(node.getGrantingRoles().isEmpty());
        assertTrue(node.getCanRestoreRoleAccess());
        assertFalse(node.getCanGrant());
    }

    @Test
    void allowOverridePlusRoleGrantKeepsRoleContext() {
        capabilities.add(capability("inventory.item.edit", "inventory", "item", "edit", "company", "active"));
        assignments.add(assignment(1L, "InventoryClerk", "company", null));
        grants.add(grant("InventoryClerk", "inventory.item.edit", "company"));
        overrides.add(override(910L, "inventory.item.edit", "allow", "company", null));

        AccessMapNode node = capabilityNode(project(), "inventory.item.edit");
        assertEquals(AccessMapNode.STATE_DIRECT_GRANTED, node.getAccessState());
        // Removing the direct grant must be explainable: the role path remains visible.
        assertEquals(1, node.getGrantingRoles().size());
        assertTrue(node.getCanRemoveDirectGrant());
    }

    @Test
    void multipleRolesGrantingSameCapabilityAreAllReported() {
        capabilities.add(capability("inventory.item.read", "inventory", "item", "read", "company", "active"));
        assignments.add(assignment(1L, "InventoryClerk", "company", null));
        assignments.add(assignment(2L, "BranchManager", "company", null));
        grants.add(grant("InventoryClerk", "inventory.item.read", "company"));
        grants.add(grant("BranchManager", "inventory.item.read", "company"));

        AccessMapNode node = capabilityNode(project(), "inventory.item.read");
        assertEquals(2, node.getGrantingRoles().size());
    }

    @Test
    void branchScopedAssignmentAppliesOnlyToItsBranch() {
        capabilities.add(capability("inventory.item.read", "inventory", "item", "read", "branch", "active"));
        assignments.add(assignment(1L, "InventoryClerk", "branch", 99));
        grants.add(grant("InventoryClerk", "inventory.item.read", "branch"));

        AccessMapNode node = capabilityNode(project(), "inventory.item.read");
        assertEquals(AccessMapNode.STATE_SCOPE_MISMATCH, node.getAccessState());
        assertFalse(node.getEffectiveAccess());
        assertEquals(1, node.getInapplicableRoles().size());
        // Grant is legitimate in the current branch context via an override.
        assertTrue(node.getCanGrant());
    }

    @Test
    void companyScopedAssignmentOfBranchCapabilityIsInert() {
        // Known model inconsistency: resolves to (branch, null) and never applies anywhere.
        capabilities.add(capability("inventory.item.export", "inventory", "item", "export", "branch", "active"));
        assignments.add(assignment(1L, "InventoryClerk", "company", null));
        grants.add(grant("InventoryClerk", "inventory.item.export", "branch"));

        AccessMapNode node = capabilityNode(project(), "inventory.item.export");
        assertEquals(AccessMapNode.STATE_SCOPE_MISMATCH, node.getAccessState());
        assertEquals(1, node.getInapplicableRoles().size());
        assertFalse(node.getEffectiveAccess());
    }

    @Test
    void moduleLockedWhenModuleUnavailableAndNotGranted() {
        effectiveModules.clear();
        effectiveModules.add(new EffectiveModuleConfig("inventory", "Inventory", "operations", false, "package_locked", "standard"));
        capabilities.add(capability("inventory.item.read", "inventory", "item", "read", "company", "active"));

        AccessMapNode node = capabilityNode(project(), "inventory.item.read");
        assertEquals(AccessMapNode.STATE_MODULE_LOCKED, node.getAccessState());
        assertFalse(node.getCanGrant());
        assertEquals(AccessMapNode.REASON_MODULE_PACKAGE_LOCKED, node.getActionBlockedReason());
    }

    @Test
    void grantedCapabilityWithUnavailableModuleStaysGrantedButUnusable() {
        effectiveModules.clear();
        effectiveModules.add(new EffectiveModuleConfig("inventory", "Inventory", "operations", false, "tenant_override", "standard"));
        capabilities.add(capability("inventory.item.read", "inventory", "item", "read", "company", "active"));
        assignments.add(assignment(1L, "InventoryClerk", "company", null));
        grants.add(grant("InventoryClerk", "inventory.item.read", "company"));

        AccessMapNode node = capabilityNode(project(), "inventory.item.read");
        assertEquals(AccessMapNode.STATE_ROLE_GRANTED, node.getAccessState());
        assertTrue(node.getCapabilityGranted());
        assertFalse(node.getUsableAccess());
        assertFalse(node.getModuleAvailable());
    }

    @Test
    void deprecatedCapabilityHasNoActions() {
        capabilities.add(capability("inventory.item.legacy", "inventory", "item", "legacy", "company", "deprecated"));
        assignments.add(assignment(1L, "InventoryClerk", "company", null));
        grants.add(grant("InventoryClerk", "inventory.item.legacy", "company"));

        AccessMapNode node = capabilityNode(project(), "inventory.item.legacy");
        assertEquals(AccessMapNode.STATE_DEPRECATED, node.getAccessState());
        assertFalse(node.getCanGrant());
        assertFalse(node.getCanBlock());
        assertEquals(AccessMapNode.REASON_CAPABILITY_DEPRECATED, node.getActionBlockedReason());
    }

    @Test
    void globalAdminScopeCannotBeManaged() {
        capabilities.add(capability("platform.admin.read", "inventory", "admin", "read", "global_admin", "active"));

        AccessMapNode node = capabilityNode(project(), "platform.admin.read");
        assertFalse(node.getCanGrant());
        assertEquals(AccessMapNode.REASON_SCOPE_NOT_APPLICABLE, node.getActionBlockedReason());
    }

    @Test
    void notGrantedCapabilityIsGrantable() {
        capabilities.add(capability("inventory.item.edit", "inventory", "item", "edit", "branch", "active"));

        AccessMapNode node = capabilityNode(project(), "inventory.item.edit");
        assertEquals(AccessMapNode.STATE_NOT_GRANTED, node.getAccessState());
        assertTrue(node.getCanGrant());
        assertEquals(BRANCH_ID, node.getScopeBranchId());
        assertNull(node.getActionBlockedReason());
    }

    // ---------- modes ----------

    @Test
    void readOnlyAdminGetsNoActions() {
        canMutate = false;
        capabilities.add(capability("inventory.item.edit", "inventory", "item", "edit", "company", "active"));

        UserAccessMapResponse response = project();
        assertFalse(response.isCanMutate());
        AccessMapNode node = capabilityNode(response, "inventory.item.edit");
        assertFalse(node.getCanGrant());
        assertEquals(AccessMapNode.REASON_READ_ONLY, node.getActionBlockedReason());
    }

    @Test
    void ownerTargetIsFlaggedAndUnmanaged() {
        targetUser = user("boss", "Big", "Boss", "Owner");
        capabilities.add(capability("inventory.item.edit", "inventory", "item", "edit", "company", "active"));

        UserAccessMapResponse response = project();
        assertTrue(response.getUser().isOwnerBypass());
        AccessMapNode node = capabilityNode(response, "inventory.item.edit");
        assertEquals(AccessMapNode.REASON_OWNER_BYPASS, node.getActionBlockedReason());
        assertFalse(node.getCanGrant());
    }

    // ---------- structure, aggregates, summary ----------

    @Test
    void hierarchyNodesAndEdgesAreBuilt() {
        capabilities.add(capability("inventory.item.read", "inventory", "item", "read", "company", "active"));
        capabilities.add(capability("inventory.item.edit", "inventory", "item", "edit", "company", "active"));

        UserAccessMapResponse response = project();
        assertNotNull(node(response, "user:" + USER_ID));
        AccessMapNode moduleNode = node(response, "module:inventory");
        AccessMapNode resourceNode = node(response, "resource:inventory:item");
        assertEquals("user:" + USER_ID, moduleNode.getParentId());
        assertEquals("module:inventory", resourceNode.getParentId());
        assertEquals("resource:inventory:item", capabilityNode(response, "inventory.item.read").getParentId());
        // 1 user + 1 module + 1 resource + 2 capabilities
        assertEquals(5, response.getNodes().size());
        assertEquals(4, response.getEdges().size());
    }

    @Test
    void aggregateCountsUseFullCapabilitySet() {
        capabilities.add(capability("inventory.item.read", "inventory", "item", "read", "company", "active"));
        capabilities.add(capability("inventory.item.edit", "inventory", "item", "edit", "company", "active"));
        capabilities.add(capability("inventory.item.delete", "inventory", "item", "delete", "company", "active"));
        assignments.add(assignment(1L, "InventoryClerk", "company", null));
        grants.add(grant("InventoryClerk", "inventory.item.read", "company"));

        UserAccessMapResponse response = project();
        AccessMapNode resourceNode = node(response, "resource:inventory:item");
        assertEquals(1, resourceNode.getAccessibleCount());
        assertEquals(3, resourceNode.getTotalCount());
        assertEquals(AccessMapNode.AGGREGATE_PARTIAL, resourceNode.getAggregateState());
    }

    @Test
    void moduleAggregateLockedWhenModuleUnavailable() {
        effectiveModules.clear();
        effectiveModules.add(new EffectiveModuleConfig("inventory", "Inventory", "operations", false, "package_locked", "standard"));
        capabilities.add(capability("inventory.item.read", "inventory", "item", "read", "company", "active"));

        AccessMapNode moduleNode = node(project(), "module:inventory");
        assertEquals(AccessMapNode.AGGREGATE_LOCKED, moduleNode.getAggregateState());
        assertFalse(moduleNode.getModuleAvailable());
    }

    @Test
    void conflictAggregateWhenDenyBlocksRoleGrant() {
        capabilities.add(capability("inventory.item.read", "inventory", "item", "read", "branch", "active"));
        capabilities.add(capability("inventory.item.edit", "inventory", "item", "edit", "branch", "active"));
        assignments.add(assignment(1L, "InventoryClerk", "branch", BRANCH_ID));
        grants.add(grant("InventoryClerk", "inventory.item.read", "branch"));
        grants.add(grant("InventoryClerk", "inventory.item.edit", "branch"));
        overrides.add(override(950L, "inventory.item.read", "deny", "branch", BRANCH_ID));

        AccessMapNode resourceNode = node(project(), "resource:inventory:item");
        assertEquals(AccessMapNode.AGGREGATE_CONFLICT, resourceNode.getAggregateState());
    }

    @Test
    void summaryCountsAreConsistent() {
        capabilities.add(capability("inventory.item.read", "inventory", "item", "read", "company", "active"));
        capabilities.add(capability("inventory.item.create", "inventory", "item", "create", "branch", "active"));
        capabilities.add(capability("inventory.item.delete", "inventory", "item", "delete", "branch", "active"));
        capabilities.add(capability("inventory.item.legacy", "inventory", "item", "legacy", "company", "deprecated"));
        assignments.add(assignment(1L, "InventoryClerk", "company", null));
        grants.add(grant("InventoryClerk", "inventory.item.read", "company"));
        overrides.add(override(1L, "inventory.item.create", "allow", "branch", BRANCH_ID));
        overrides.add(override(2L, "inventory.item.delete", "deny", "branch", BRANCH_ID));

        UserAccessMapResponse response = project();
        assertEquals(2, response.getSummary().getAccessibleCapabilities());
        assertEquals(4, response.getSummary().getTotalCapabilities());
        assertEquals(1, response.getSummary().getDirectGrants());
        assertEquals(1, response.getSummary().getRoleGrants());
        assertEquals(1, response.getSummary().getExplicitDenies());
        assertEquals(1, response.getSummary().getDeprecatedCapabilities());
    }

    @Test
    void assignedRolesAreListedOnUserSummary() {
        capabilities.add(capability("inventory.item.read", "inventory", "item", "read", "company", "active"));
        assignments.add(assignment(1L, "InventoryClerk", "company", null));
        assignments.add(assignment(2L, "BranchManager", "branch", BRANCH_ID));

        UserAccessMapResponse response = project();
        assertEquals(2, response.getUser().getAssignedRoles().size());
        assertEquals("Ahmed Mohamed", response.getUser().getDisplayName());
    }
}
