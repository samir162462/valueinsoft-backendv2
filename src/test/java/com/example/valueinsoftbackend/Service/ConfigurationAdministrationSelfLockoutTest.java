package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbBranch;
import com.example.valueinsoftbackend.DatabaseRequests.DbCompany;
import com.example.valueinsoftbackend.DatabaseRequests.DbCompanyTemplates;
import com.example.valueinsoftbackend.DatabaseRequests.DbConfigurationAdmin;
import com.example.valueinsoftbackend.DatabaseRequests.DbPackagePlans;
import com.example.valueinsoftbackend.DatabaseRequests.DbPlatformCapabilities;
import com.example.valueinsoftbackend.DatabaseRequests.DbPlatformModules;
import com.example.valueinsoftbackend.DatabaseRequests.DbRoleDefinitions;
import com.example.valueinsoftbackend.DatabaseRequests.DbRoleGrants;
import com.example.valueinsoftbackend.DatabaseRequests.DbTenantAccessAuditEvents;
import com.example.valueinsoftbackend.DatabaseRequests.DbTenants;
import com.example.valueinsoftbackend.DatabaseRequests.DbUsers;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Company;
import com.example.valueinsoftbackend.Model.Configuration.ConfigurationAdminUserSummary;
import com.example.valueinsoftbackend.Model.Configuration.PlatformCapabilityConfig;
import com.example.valueinsoftbackend.Model.Request.Configuration.SaveUserGrantOverrideRequest;
import com.example.valueinsoftbackend.Model.User;
import com.example.valueinsoftbackend.Service.security.AuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfigurationAdministrationSelfLockoutTest {

    private static final int TENANT_ID = 12;
    private static final int ACTOR_USER_ID = 7;
    private static final String PRINCIPAL = "admin : Manager";

    private DbUsers dbUsers;
    private DbCompany dbCompany;
    private DbPlatformCapabilities dbPlatformCapabilities;
    private DbConfigurationAdmin dbConfigurationAdmin;
    private AuthorizationService authorizationService;
    private DbTenantAccessAuditEvents auditEvents;
    private ConfigurationAdministrationService service;

    @BeforeEach
    void setUp() {
        dbUsers = mock(DbUsers.class);
        dbCompany = mock(DbCompany.class);
        dbPlatformCapabilities = mock(DbPlatformCapabilities.class);
        dbConfigurationAdmin = mock(DbConfigurationAdmin.class);
        authorizationService = mock(AuthorizationService.class);
        auditEvents = mock(DbTenantAccessAuditEvents.class);

        service = new ConfigurationAdministrationService(
                dbUsers,
                dbCompany,
                mock(DbBranch.class),
                mock(DbTenants.class),
                mock(DbPackagePlans.class),
                mock(DbCompanyTemplates.class),
                dbPlatformCapabilities,
                mock(DbPlatformModules.class),
                mock(DbRoleDefinitions.class),
                mock(DbRoleGrants.class),
                dbConfigurationAdmin,
                mock(EffectiveConfigurationService.class),
                authorizationService,
                auditEvents
        );

        User actor = mock(User.class);
        when(actor.getUserId()).thenReturn(ACTOR_USER_ID);
        when(actor.getUserName()).thenReturn("admin");
        when(actor.getRole()).thenReturn("Manager");
        when(dbUsers.getUser("admin")).thenReturn(actor);

        Company company = mock(Company.class);
        when(company.getCompanyId()).thenReturn(TENANT_ID);
        when(company.getOwnerId()).thenReturn(ACTOR_USER_ID);
        when(dbCompany.getCompanyById(TENANT_ID)).thenReturn(company);

        PlatformCapabilityConfig capability = new PlatformCapabilityConfig(
                "users.account.edit",
                "users",
                "account",
                "edit",
                "company",
                "active",
                "Edit user accounts"
        );
        when(dbPlatformCapabilities.getCapability("users.account.edit")).thenReturn(capability);
        when(dbConfigurationAdmin.getUsersForTenant(TENANT_ID, null)).thenReturn(List.of(
                new ConfigurationAdminUserSummary(
                        ACTOR_USER_ID,
                        "admin",
                        "admin@example.com",
                        "Admin",
                        "User",
                        "",
                        "Manager",
                        1,
                        "Main",
                        null
                )
        ));
    }

    @Test
    void denyingOwnLastEditCapabilityIsRejectedBeforeAudit() {
        when(authorizationService.hasAuthenticatedCapability(
                PRINCIPAL,
                TENANT_ID,
                null,
                "users.account.edit"
        )).thenReturn(false);

        SaveUserGrantOverrideRequest request = new SaveUserGrantOverrideRequest();
        request.setCapabilityKey("users.account.edit");
        request.setScopeType("company");
        request.setGrantMode("deny");
        request.setReason("temporary block");

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.saveUserGrantOverrideForAuthenticatedUser(
                        PRINCIPAL,
                        TENANT_ID,
                        null,
                        ACTOR_USER_ID,
                        request
                )
        );

        assertEquals("SELF_LOCKOUT_PROTECTED", exception.getCode());
        verify(dbConfigurationAdmin).upsertTenantUserGrantOverride(
                TENANT_ID,
                ACTOR_USER_ID,
                "users.account.edit",
                "deny",
                "company",
                null,
                "temporary_block",
                "admin"
        );
        verify(auditEvents, never()).insertEvent(
                any(Integer.class), any(Integer.class), any(Integer.class),
                any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void removingOwnLastEditCapabilityIsRejectedBeforeAudit() {
        when(authorizationService.hasAuthenticatedCapability(
                PRINCIPAL,
                TENANT_ID,
                null,
                "users.account.edit"
        )).thenReturn(false);
        when(dbConfigurationAdmin.deleteTenantUserGrantOverride(
                TENANT_ID,
                ACTOR_USER_ID,
                "users.account.edit",
                "company",
                null
        )).thenReturn(1);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.removeUserGrantOverrideForAuthenticatedUser(
                        PRINCIPAL,
                        TENANT_ID,
                        null,
                        ACTOR_USER_ID,
                        "users.account.edit",
                        "company",
                        null
                )
        );

        assertEquals("SELF_LOCKOUT_PROTECTED", exception.getCode());
        verify(dbConfigurationAdmin).deleteTenantUserGrantOverride(
                TENANT_ID,
                ACTOR_USER_ID,
                "users.account.edit",
                "company",
                null
        );
        verify(auditEvents, never()).insertEvent(
                any(Integer.class), any(Integer.class), any(Integer.class),
                any(), any(), any(), any(), any(), any()
        );
    }
}
