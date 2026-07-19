package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbConfigurationAdmin;
import com.example.valueinsoftbackend.DatabaseRequests.DbPlatformCapabilities;
import com.example.valueinsoftbackend.DatabaseRequests.DbPlatformModules;
import com.example.valueinsoftbackend.DatabaseRequests.DbRoleDefinitions;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Configuration.AccessMap.UserAccessMapResponse;
import com.example.valueinsoftbackend.Model.Configuration.ConfigurationAdminUserSummary;
import com.example.valueinsoftbackend.Model.Configuration.EffectiveConfiguration;
import com.example.valueinsoftbackend.Service.security.AuthenticatedEffectiveConfigurationService;
import com.example.valueinsoftbackend.Service.security.AuthorizationService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Read-only orchestration for the target-user access map used by the visual
 * access-management graph.
 *
 * <p>Security order per request: resolve the acting admin's tenant/branch context
 * from the authenticated identity (membership enforced), require a user-administration
 * read capability, verify the target user belongs to the tenant, then project.</p>
 */
@Service
public class UserAccessMapService {

    private final AuthenticatedEffectiveConfigurationService authenticatedEffectiveConfigurationService;
    private final AuthorizationService authorizationService;
    private final EffectiveConfigurationService effectiveConfigurationService;
    private final UserAccessProjectionService userAccessProjectionService;
    private final DbConfigurationAdmin dbConfigurationAdmin;
    private final DbPlatformCapabilities dbPlatformCapabilities;
    private final DbPlatformModules dbPlatformModules;
    private final DbRoleDefinitions dbRoleDefinitions;

    public UserAccessMapService(AuthenticatedEffectiveConfigurationService authenticatedEffectiveConfigurationService,
                                AuthorizationService authorizationService,
                                EffectiveConfigurationService effectiveConfigurationService,
                                UserAccessProjectionService userAccessProjectionService,
                                DbConfigurationAdmin dbConfigurationAdmin,
                                DbPlatformCapabilities dbPlatformCapabilities,
                                DbPlatformModules dbPlatformModules,
                                DbRoleDefinitions dbRoleDefinitions) {
        this.authenticatedEffectiveConfigurationService = authenticatedEffectiveConfigurationService;
        this.authorizationService = authorizationService;
        this.effectiveConfigurationService = effectiveConfigurationService;
        this.userAccessProjectionService = userAccessProjectionService;
        this.dbConfigurationAdmin = dbConfigurationAdmin;
        this.dbPlatformCapabilities = dbPlatformCapabilities;
        this.dbPlatformModules = dbPlatformModules;
        this.dbRoleDefinitions = dbRoleDefinitions;
    }

    public UserAccessMapResponse getAccessMapForAuthenticatedUser(String authenticatedName,
                                                                  Integer requestedTenantId,
                                                                  Integer requestedBranchId,
                                                                  int targetUserId) {
        // 1. Tenant/branch context from the authenticated identity (membership enforced,
        //    including branch-belongs-to-tenant; P0-2 path).
        AuthenticatedEffectiveConfigurationService.ResolvedTenantContext context =
                authenticatedEffectiveConfigurationService.resolveTenantContextForAuthenticatedUser(
                        authenticatedName, requestedTenantId, requestedBranchId);
        int tenantId = context.getTenantId();
        Integer activeBranchId = context.getActiveBranchId();

        // 2. Read guard: user-administration read tier. Edit capability implies mutation rights.
        boolean canMutate = authorizationService.hasAuthenticatedCapability(
                authenticatedName, tenantId, activeBranchId, "users.account.edit");
        boolean canRead = canMutate || authorizationService.hasAuthenticatedCapability(
                authenticatedName, tenantId, activeBranchId, "users.account.read");
        if (!canRead) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_MAP_DENIED",
                    "The authenticated user cannot view the user access map");
        }

        // 3. Target user must belong to the resolved tenant.
        ConfigurationAdminUserSummary targetUser = requireTenantUser(tenantId, targetUserId);

        // 4. Load configuration state. EffectiveConfiguration for the TARGET user carries the
        //    tenant's effective modules plus the target's role assignments, the grants of those
        //    roles, and the target's overrides — resolved by the same trusted service.
        EffectiveConfiguration targetConfiguration =
                effectiveConfigurationService.getEffectiveConfiguration(tenantId, targetUserId, activeBranchId);

        // 5. Project the explainable access map over the FULL capability catalog
        //    (including deprecated capabilities).
        return userAccessProjectionService.buildAccessMap(new UserAccessProjectionService.ProjectionInput(
                targetUser,
                tenantId,
                activeBranchId,
                canMutate,
                dbPlatformCapabilities.getAllCapabilities(),
                dbPlatformModules.getAllModules(),
                targetConfiguration.getModules(),
                targetConfiguration.getRoleAssignments(),
                targetConfiguration.getRoleGrants(),
                targetConfiguration.getUserGrantOverrides(),
                dbRoleDefinitions.getActiveRoleDefinitions()
        ));
    }

    private ConfigurationAdminUserSummary requireTenantUser(int tenantId, int targetUserId) {
        List<ConfigurationAdminUserSummary> users = dbConfigurationAdmin.getUsersForTenant(tenantId, null);
        for (ConfigurationAdminUserSummary user : users) {
            if (user.getUserId() == targetUserId) {
                return user;
            }
        }
        throw new ApiException(HttpStatus.FORBIDDEN, "USER_ACCESS_DENIED",
                "User does not belong to the resolved tenant");
    }
}
