package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbConfigurationAdmin;
import com.example.valueinsoftbackend.DatabaseRequests.DbPlatformCapabilities;
import com.example.valueinsoftbackend.DatabaseRequests.DbPlatformModules;
import com.example.valueinsoftbackend.DatabaseRequests.DbRoleDefinitions;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Configuration.AccessMap.UserAccessMapResponse;
import com.example.valueinsoftbackend.Model.Configuration.ConfigurationAdminUserSummary;
import com.example.valueinsoftbackend.Model.Configuration.EffectiveConfiguration;
import com.example.valueinsoftbackend.Model.Configuration.EffectiveModuleConfig;
import com.example.valueinsoftbackend.Model.Configuration.PlatformCapabilityConfig;
import com.example.valueinsoftbackend.Model.Configuration.PlatformModuleConfig;
import com.example.valueinsoftbackend.Service.security.AuthenticatedEffectiveConfigurationService;
import com.example.valueinsoftbackend.Service.security.AuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class UserAccessMapServiceTest {

    private static final String PRINCIPAL = "admin : Manager";
    private static final int TENANT_ID = 12;
    private static final Integer BRANCH_ID = 3;
    private static final int TARGET_USER_ID = 41;

    private AuthenticatedEffectiveConfigurationService contextService;
    private AuthorizationService authorizationService;
    private EffectiveConfigurationService effectiveConfigurationService;
    private DbConfigurationAdmin dbConfigurationAdmin;
    private DbPlatformCapabilities dbPlatformCapabilities;
    private DbPlatformModules dbPlatformModules;
    private DbRoleDefinitions dbRoleDefinitions;
    private UserAccessMapService service;

    @BeforeEach
    void setUp() {
        contextService = Mockito.mock(AuthenticatedEffectiveConfigurationService.class);
        authorizationService = Mockito.mock(AuthorizationService.class);
        effectiveConfigurationService = Mockito.mock(EffectiveConfigurationService.class);
        dbConfigurationAdmin = Mockito.mock(DbConfigurationAdmin.class);
        dbPlatformCapabilities = Mockito.mock(DbPlatformCapabilities.class);
        dbPlatformModules = Mockito.mock(DbPlatformModules.class);
        dbRoleDefinitions = Mockito.mock(DbRoleDefinitions.class);

        service = new UserAccessMapService(
                contextService,
                authorizationService,
                effectiveConfigurationService,
                new UserAccessProjectionService(),
                dbConfigurationAdmin,
                dbPlatformCapabilities,
                dbPlatformModules,
                dbRoleDefinitions
        );

        AuthenticatedEffectiveConfigurationService.ResolvedTenantContext context =
                Mockito.mock(AuthenticatedEffectiveConfigurationService.ResolvedTenantContext.class);
        when(context.getTenantId()).thenReturn(TENANT_ID);
        when(context.getActiveBranchId()).thenReturn(BRANCH_ID);
        when(contextService.resolveTenantContextForAuthenticatedUser(eq(PRINCIPAL), any(), any()))
                .thenReturn(context);

        when(dbConfigurationAdmin.getUsersForTenant(TENANT_ID, null)).thenReturn(List.of(
                new ConfigurationAdminUserSummary(TARGET_USER_ID, "ahmed", "a@x.com", "Ahmed", "Mohamed",
                        "0100", "Manager", BRANCH_ID, "Cairo", null)
        ));

        EffectiveConfiguration targetConfiguration = Mockito.mock(EffectiveConfiguration.class);
        when(targetConfiguration.getModules()).thenReturn(new ArrayList<>(List.of(
                new EffectiveModuleConfig("inventory", "Inventory", "operations", true, "package", "standard"))));
        when(targetConfiguration.getRoleAssignments()).thenReturn(new ArrayList<>());
        when(targetConfiguration.getRoleGrants()).thenReturn(new ArrayList<>());
        when(targetConfiguration.getUserGrantOverrides()).thenReturn(new ArrayList<>());
        when(effectiveConfigurationService.getEffectiveConfiguration(TENANT_ID, TARGET_USER_ID, BRANCH_ID))
                .thenReturn(targetConfiguration);

        when(dbPlatformCapabilities.getAllCapabilities()).thenReturn(List.of(
                new PlatformCapabilityConfig("inventory.item.read", "inventory", "item", "read",
                        "company", "active", "Read items")));
        when(dbPlatformModules.getAllModules()).thenReturn(List.of(
                new PlatformModuleConfig("inventory", "Inventory", "operations", "active", true, "v1", "Inventory")));
        when(dbRoleDefinitions.getActiveRoleDefinitions()).thenReturn(List.of());
    }

    private void grantCapability(String capabilityKey, boolean value) {
        when(authorizationService.hasAuthenticatedCapability(eq(PRINCIPAL), eq(TENANT_ID), eq(BRANCH_ID), eq(capabilityKey)))
                .thenReturn(value);
    }

    @Test
    void adminWithoutUserAdministrationCapabilityIsRejected() {
        grantCapability("users.account.edit", false);
        grantCapability("users.account.read", false);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.getAccessMapForAuthenticatedUser(PRINCIPAL, TENANT_ID, BRANCH_ID, TARGET_USER_ID));
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertEquals("ACCESS_MAP_DENIED", exception.getCode());
    }

    @Test
    void readOnlyAdminGetsMapWithoutMutationRights() {
        grantCapability("users.account.edit", false);
        grantCapability("users.account.read", true);

        UserAccessMapResponse response =
                service.getAccessMapForAuthenticatedUser(PRINCIPAL, TENANT_ID, BRANCH_ID, TARGET_USER_ID);
        assertFalse(response.isCanMutate());
        assertEquals(TENANT_ID, response.getTenantId());
        assertEquals(BRANCH_ID, response.getActiveBranchId());
    }

    @Test
    void editorAdminGetsMutableMap() {
        grantCapability("users.account.edit", true);

        UserAccessMapResponse response =
                service.getAccessMapForAuthenticatedUser(PRINCIPAL, TENANT_ID, BRANCH_ID, TARGET_USER_ID);
        assertTrue(response.isCanMutate());
        assertEquals(TARGET_USER_ID, response.getUser().getUserId());
        assertEquals(1, response.getSummary().getTotalCapabilities());
    }

    @Test
    void targetUserOutsideTenantIsRejected() {
        grantCapability("users.account.edit", true);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.getAccessMapForAuthenticatedUser(PRINCIPAL, TENANT_ID, BRANCH_ID, 999));
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertEquals("USER_ACCESS_DENIED", exception.getCode());
    }

    @Test
    void tenantMembershipFailurePropagates() {
        when(contextService.resolveTenantContextForAuthenticatedUser(eq(PRINCIPAL), anyInt(), anyInt()))
                .thenThrow(new ApiException(HttpStatus.FORBIDDEN, "TENANT_ACCESS_DENIED",
                        "User does not belong to the requested tenant"));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.getAccessMapForAuthenticatedUser(PRINCIPAL, 777, BRANCH_ID, TARGET_USER_ID));
        assertEquals("TENANT_ACCESS_DENIED", exception.getCode());
    }

    @Test
    void ownerPrincipalActingInOwnTenantIsAllowed() {
        // AuthorizationService returns true for owners inside their tenant; the map service
        // only consumes that boolean, so an owner acting in-tenant gets a mutable map.
        String ownerPrincipal = "boss : Owner";
        AuthenticatedEffectiveConfigurationService.ResolvedTenantContext context =
                Mockito.mock(AuthenticatedEffectiveConfigurationService.ResolvedTenantContext.class);
        when(context.getTenantId()).thenReturn(TENANT_ID);
        when(context.getActiveBranchId()).thenReturn(BRANCH_ID);
        when(contextService.resolveTenantContextForAuthenticatedUser(eq(ownerPrincipal), any(), any()))
                .thenReturn(context);
        when(authorizationService.hasAuthenticatedCapability(eq(ownerPrincipal), eq(TENANT_ID), eq(BRANCH_ID),
                eq("users.account.edit"))).thenReturn(true);

        UserAccessMapResponse response =
                service.getAccessMapForAuthenticatedUser(ownerPrincipal, TENANT_ID, BRANCH_ID, TARGET_USER_ID);
        assertTrue(response.isCanMutate());
    }

    @Test
    void ownerPrincipalCrossTenantIsRejected() {
        // Cross-tenant owners are stopped by tenant-context resolution before any capability logic.
        String ownerPrincipal = "boss : Owner";
        when(contextService.resolveTenantContextForAuthenticatedUser(eq(ownerPrincipal), any(), any()))
                .thenThrow(new ApiException(HttpStatus.FORBIDDEN, "TENANT_ACCESS_DENIED",
                        "User does not belong to the requested tenant"));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.getAccessMapForAuthenticatedUser(ownerPrincipal, 777, BRANCH_ID, TARGET_USER_ID));
        assertEquals("TENANT_ACCESS_DENIED", exception.getCode());
    }
}
