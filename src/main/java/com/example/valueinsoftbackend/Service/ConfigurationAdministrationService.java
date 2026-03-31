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
import com.example.valueinsoftbackend.DatabaseRequests.DbTenants;
import com.example.valueinsoftbackend.DatabaseRequests.DbUsers;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Branch;
import com.example.valueinsoftbackend.Model.Company;
import com.example.valueinsoftbackend.Model.Configuration.CompanyTemplateConfig;
import com.example.valueinsoftbackend.Model.Configuration.ConfigurationAdminUserSummary;
import com.example.valueinsoftbackend.Model.Configuration.EffectiveConfiguration;
import com.example.valueinsoftbackend.Model.Configuration.PackagePlanConfig;
import com.example.valueinsoftbackend.Model.Configuration.PlatformCapabilityConfig;
import com.example.valueinsoftbackend.Model.Configuration.RoleDefinitionConfig;
import com.example.valueinsoftbackend.Model.Configuration.RoleGrantConfig;
import com.example.valueinsoftbackend.Model.Configuration.TenantAdminPortalConfig;
import com.example.valueinsoftbackend.Model.Configuration.TenantConfig;
import com.example.valueinsoftbackend.Model.Request.Configuration.SaveRoleGrantRequest;
import com.example.valueinsoftbackend.Model.Request.Configuration.SaveTenantRoleAssignmentRequest;
import com.example.valueinsoftbackend.Model.Request.Configuration.SaveUserGrantOverrideRequest;
import com.example.valueinsoftbackend.Model.Request.Configuration.UpdateTenantModuleOverrideRequest;
import com.example.valueinsoftbackend.Model.Request.Configuration.UpdateTenantWorkflowOverrideRequest;
import com.example.valueinsoftbackend.Model.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ConfigurationAdministrationService {

    private final DbUsers dbUsers;
    private final DbCompany dbCompany;
    private final DbBranch dbBranch;
    private final DbTenants dbTenants;
    private final DbPackagePlans dbPackagePlans;
    private final DbCompanyTemplates dbCompanyTemplates;
    private final DbPlatformCapabilities dbPlatformCapabilities;
    private final DbPlatformModules dbPlatformModules;
    private final DbRoleDefinitions dbRoleDefinitions;
    private final DbRoleGrants dbRoleGrants;
    private final DbConfigurationAdmin dbConfigurationAdmin;
    private final EffectiveConfigurationService effectiveConfigurationService;
    private final AuthorizationService authorizationService;

    public ConfigurationAdministrationService(DbUsers dbUsers,
                                              DbCompany dbCompany,
                                              DbBranch dbBranch,
                                              DbTenants dbTenants,
                                              DbPackagePlans dbPackagePlans,
                                              DbCompanyTemplates dbCompanyTemplates,
                                              DbPlatformCapabilities dbPlatformCapabilities,
                                              DbPlatformModules dbPlatformModules,
                                              DbRoleDefinitions dbRoleDefinitions,
                                              DbRoleGrants dbRoleGrants,
                                              DbConfigurationAdmin dbConfigurationAdmin,
                                              EffectiveConfigurationService effectiveConfigurationService,
                                              AuthorizationService authorizationService) {
        this.dbUsers = dbUsers;
        this.dbCompany = dbCompany;
        this.dbBranch = dbBranch;
        this.dbTenants = dbTenants;
        this.dbPackagePlans = dbPackagePlans;
        this.dbCompanyTemplates = dbCompanyTemplates;
        this.dbPlatformCapabilities = dbPlatformCapabilities;
        this.dbPlatformModules = dbPlatformModules;
        this.dbRoleDefinitions = dbRoleDefinitions;
        this.dbRoleGrants = dbRoleGrants;
        this.dbConfigurationAdmin = dbConfigurationAdmin;
        this.effectiveConfigurationService = effectiveConfigurationService;
        this.authorizationService = authorizationService;
    }

    public TenantAdminPortalConfig getPortalForAuthenticatedUser(String authenticatedName,
                                                                 Integer requestedTenantId,
                                                                 Integer requestedBranchId) {
        ResolvedAdminContext context = resolveAdminContext(authenticatedName, requestedTenantId, requestedBranchId);
        assertPortalReadable(authenticatedName, context.tenantId, context.activeBranchId);

        TenantConfig tenant = requireTenant(context.tenantId);
        PackagePlanConfig packagePlan = requirePackagePlan(tenant.getPackageId());
        CompanyTemplateConfig companyTemplate = requireCompanyTemplate(tenant.getTemplateId());
        EffectiveConfiguration effectiveConfiguration = effectiveConfigurationService.getEffectiveConfiguration(
                context.tenantId,
                context.user.getUserId(),
                context.activeBranchId
        );

        ArrayList<RoleDefinitionConfig> roleDefinitions = new ArrayList<>(dbRoleDefinitions.getActiveRoleDefinitions());
        ArrayList<RoleGrantConfig> roleGrants = new ArrayList<>(dbRoleGrants.getGrantsForRoleIds(extractRoleIds(roleDefinitions)));

        return new TenantAdminPortalConfig(
                context.tenantId,
                context.activeBranchId,
                tenant,
                packagePlan,
                companyTemplate,
                new ArrayList<>(dbBranch.getBranchByCompanyId(context.tenantId)),
                new ArrayList<>(dbConfigurationAdmin.getUsersForTenant(context.tenantId, context.activeBranchId)),
                roleDefinitions,
                roleGrants,
                new ArrayList<>(dbConfigurationAdmin.getTenantRoleAssignments(context.tenantId, context.activeBranchId)),
                new ArrayList<>(dbConfigurationAdmin.getTenantUserGrantOverrides(context.tenantId, context.activeBranchId)),
                new ArrayList<>(dbPlatformModules.getAllModules()),
                new ArrayList<>(dbPlatformCapabilities.getActiveCapabilities()),
                effectiveConfiguration.getModules(),
                new ArrayList<>(dbTenants.getTenantModuleOverrides(context.tenantId)),
                effectiveConfiguration.getWorkflowFlags(),
                new ArrayList<>(dbTenants.getTenantWorkflowOverrides(context.tenantId))
        );
    }

    public TenantAdminPortalConfig updateModuleOverrideForAuthenticatedUser(String authenticatedName,
                                                                            Integer requestedTenantId,
                                                                            Integer requestedBranchId,
                                                                            String moduleId,
                                                                            UpdateTenantModuleOverrideRequest request) {
        ResolvedAdminContext context = resolveAdminContext(authenticatedName, requestedTenantId, requestedBranchId);
        authorizationService.assertAuthenticatedCapability(authenticatedName, context.tenantId, context.activeBranchId, "company.settings.edit");

        dbConfigurationAdmin.upsertTenantModuleOverride(
                context.tenantId,
                moduleId,
                Boolean.TRUE.equals(request.getEnabled()),
                normalizeModuleMode(request.getMode()),
                normalizeReason(request.getReason(), "admin_portal"),
                "admin",
                "v1"
        );

        return getPortalForAuthenticatedUser(authenticatedName, context.tenantId, context.activeBranchId);
    }

    public TenantAdminPortalConfig updateWorkflowOverrideForAuthenticatedUser(String authenticatedName,
                                                                              Integer requestedTenantId,
                                                                              Integer requestedBranchId,
                                                                              String flagKey,
                                                                              UpdateTenantWorkflowOverrideRequest request) {
        ResolvedAdminContext context = resolveAdminContext(authenticatedName, requestedTenantId, requestedBranchId);
        authorizationService.assertAuthenticatedCapability(authenticatedName, context.tenantId, context.activeBranchId, "company.settings.edit");

        dbConfigurationAdmin.upsertTenantWorkflowOverride(
                context.tenantId,
                flagKey,
                request.getFlagValueJson(),
                normalizeReason(request.getReason(), "admin_portal"),
                "admin",
                "v1"
        );

        return getPortalForAuthenticatedUser(authenticatedName, context.tenantId, context.activeBranchId);
    }

    public TenantAdminPortalConfig saveRoleAssignmentForAuthenticatedUser(String authenticatedName,
                                                                          Integer requestedTenantId,
                                                                          Integer requestedBranchId,
                                                                          SaveTenantRoleAssignmentRequest request) {
        ResolvedAdminContext context = resolveAdminContext(authenticatedName, requestedTenantId, requestedBranchId);
        authorizationService.assertAuthenticatedCapability(authenticatedName, context.tenantId, context.activeBranchId, "users.account.edit");

        Integer scopeBranchId = request.getScopeBranchId();
        if ("branch".equalsIgnoreCase(request.getScopeType()) && scopeBranchId == null) {
            scopeBranchId = context.activeBranchId;
        }
        if (scopeBranchId != null) {
            ensureBranchBelongsToTenant(context.tenantId, scopeBranchId);
        }

        dbConfigurationAdmin.upsertTenantRoleAssignment(
                context.tenantId,
                request.getUserId(),
                request.getRoleId(),
                normalizeScopeType(request.getScopeType()),
                scopeBranchId,
                context.user.getUserId(),
                "admin"
        );

        return getPortalForAuthenticatedUser(authenticatedName, context.tenantId, context.activeBranchId);
    }

    public TenantAdminPortalConfig deactivateRoleAssignmentForAuthenticatedUser(String authenticatedName,
                                                                                Integer requestedTenantId,
                                                                                Integer requestedBranchId,
                                                                                long assignmentId) {
        ResolvedAdminContext context = resolveAdminContext(authenticatedName, requestedTenantId, requestedBranchId);
        authorizationService.assertAuthenticatedCapability(authenticatedName, context.tenantId, context.activeBranchId, "users.account.edit");
        dbConfigurationAdmin.deactivateTenantRoleAssignment(assignmentId);
        return getPortalForAuthenticatedUser(authenticatedName, context.tenantId, context.activeBranchId);
    }

    public TenantAdminPortalConfig saveRoleGrantForAuthenticatedUser(String authenticatedName,
                                                                     Integer requestedTenantId,
                                                                     Integer requestedBranchId,
                                                                     String roleId,
                                                                     SaveRoleGrantRequest request) {
        ResolvedAdminContext context = resolveAdminContext(authenticatedName, requestedTenantId, requestedBranchId);
        authorizationService.assertAuthenticatedCapability(authenticatedName, context.tenantId, context.activeBranchId, "users.account.edit");

        RoleDefinitionConfig roleDefinition = requireRoleDefinition(roleId);
        PlatformCapabilityConfig capability = requireCapability(request.getCapabilityKey());
        String normalizedScopeType = normalizeScopeType(request.getScopeType());
        if (!normalizedScopeType.equals(capability.getScopeType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ROLE_GRANT_SCOPE_MISMATCH", "Role grant scope must match the capability scope");
        }

        dbRoleGrants.upsertRoleGrant(
                roleDefinition.getRoleId(),
                capability.getCapabilityKey(),
                normalizedScopeType,
                normalizeGrantMode(request.getGrantMode()),
                "v1"
        );

        return getPortalForAuthenticatedUser(authenticatedName, context.tenantId, context.activeBranchId);
    }

    public TenantAdminPortalConfig removeRoleGrantForAuthenticatedUser(String authenticatedName,
                                                                       Integer requestedTenantId,
                                                                       Integer requestedBranchId,
                                                                       String roleId,
                                                                       String capabilityKey,
                                                                       String scopeType) {
        ResolvedAdminContext context = resolveAdminContext(authenticatedName, requestedTenantId, requestedBranchId);
        authorizationService.assertAuthenticatedCapability(authenticatedName, context.tenantId, context.activeBranchId, "users.account.edit");

        requireRoleDefinition(roleId);
        PlatformCapabilityConfig capability = requireCapability(capabilityKey);
        String normalizedScopeType = normalizeScopeType(scopeType);
        if (!normalizedScopeType.equals(capability.getScopeType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ROLE_GRANT_SCOPE_MISMATCH", "Role grant scope must match the capability scope");
        }

        dbRoleGrants.deleteRoleGrant(roleId, capability.getCapabilityKey(), normalizedScopeType);
        return getPortalForAuthenticatedUser(authenticatedName, context.tenantId, context.activeBranchId);
    }

    public TenantAdminPortalConfig saveUserGrantOverrideForAuthenticatedUser(String authenticatedName,
                                                                             Integer requestedTenantId,
                                                                             Integer requestedBranchId,
                                                                             int targetUserId,
                                                                             SaveUserGrantOverrideRequest request) {
        ResolvedAdminContext context = resolveAdminContext(authenticatedName, requestedTenantId, requestedBranchId);
        authorizationService.assertAuthenticatedCapability(authenticatedName, context.tenantId, context.activeBranchId, "users.account.edit");

        PlatformCapabilityConfig capability = requireCapability(request.getCapabilityKey());
        String normalizedScopeType = normalizeScopeType(request.getScopeType());
        if (!normalizedScopeType.equals(capability.getScopeType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "USER_OVERRIDE_SCOPE_MISMATCH", "User override scope must match the capability scope");
        }

        Integer scopeBranchId = request.getScopeBranchId();
        if ("branch".equals(normalizedScopeType)) {
            scopeBranchId = scopeBranchId != null ? scopeBranchId : context.activeBranchId;
            if (scopeBranchId == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "USER_OVERRIDE_BRANCH_REQUIRED", "Branch scope overrides require a branch target");
            }
            ensureBranchBelongsToTenant(context.tenantId, scopeBranchId);
        } else {
            scopeBranchId = null;
        }

        ensureUserBelongsToTenant(context.tenantId, targetUserId);

        dbConfigurationAdmin.upsertTenantUserGrantOverride(
                context.tenantId,
                targetUserId,
                capability.getCapabilityKey(),
                normalizeGrantMode(request.getGrantMode()),
                normalizedScopeType,
                scopeBranchId,
                normalizeReason(request.getReason(), "user_override"),
                "admin"
        );

        return getPortalForAuthenticatedUser(authenticatedName, context.tenantId, context.activeBranchId);
    }

    public TenantAdminPortalConfig removeUserGrantOverrideForAuthenticatedUser(String authenticatedName,
                                                                               Integer requestedTenantId,
                                                                               Integer requestedBranchId,
                                                                               int targetUserId,
                                                                               String capabilityKey,
                                                                               String scopeType,
                                                                               Integer scopeBranchId) {
        ResolvedAdminContext context = resolveAdminContext(authenticatedName, requestedTenantId, requestedBranchId);
        authorizationService.assertAuthenticatedCapability(authenticatedName, context.tenantId, context.activeBranchId, "users.account.edit");

        PlatformCapabilityConfig capability = requireCapability(capabilityKey);
        String normalizedScopeType = normalizeScopeType(scopeType);
        if (!normalizedScopeType.equals(capability.getScopeType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "USER_OVERRIDE_SCOPE_MISMATCH", "User override scope must match the capability scope");
        }

        ensureUserBelongsToTenant(context.tenantId, targetUserId);

        Integer normalizedScopeBranchId = "branch".equals(normalizedScopeType) ? scopeBranchId : null;
        if ("branch".equals(normalizedScopeType)) {
            if (normalizedScopeBranchId == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "USER_OVERRIDE_BRANCH_REQUIRED", "Branch scope overrides require a branch target");
            }
            ensureBranchBelongsToTenant(context.tenantId, normalizedScopeBranchId);
        }

        dbConfigurationAdmin.deleteTenantUserGrantOverride(
                context.tenantId,
                targetUserId,
                capability.getCapabilityKey(),
                normalizedScopeType,
                normalizedScopeBranchId
        );

        return getPortalForAuthenticatedUser(authenticatedName, context.tenantId, context.activeBranchId);
    }

    private void assertPortalReadable(String authenticatedName, int tenantId, Integer activeBranchId) {
        if (authorizationService.hasAuthenticatedCapability(authenticatedName, tenantId, activeBranchId, "company.settings.read")) {
            return;
        }
        if (authorizationService.hasAuthenticatedCapability(authenticatedName, tenantId, activeBranchId, "company.settings.edit")) {
            return;
        }
        if (authorizationService.hasAuthenticatedCapability(authenticatedName, tenantId, activeBranchId, "users.account.read")) {
            return;
        }
        if (authorizationService.hasAuthenticatedCapability(authenticatedName, tenantId, activeBranchId, "users.account.edit")) {
            return;
        }
        throw new ApiException(HttpStatus.FORBIDDEN, "PORTAL_ACCESS_DENIED", "The authenticated user cannot access the company admin portal");
    }

    private TenantConfig requireTenant(int tenantId) {
        TenantConfig tenant = dbTenants.getTenantById(tenantId);
        if (tenant == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "TENANT_NOT_FOUND", "Tenant not found");
        }
        return tenant;
    }

    private PackagePlanConfig requirePackagePlan(String packageId) {
        PackagePlanConfig packagePlan = dbPackagePlans.getPackagePlan(packageId);
        if (packagePlan == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PACKAGE_PLAN_NOT_FOUND", "Package plan not found");
        }
        return packagePlan;
    }

    private CompanyTemplateConfig requireCompanyTemplate(String templateId) {
        CompanyTemplateConfig companyTemplate = dbCompanyTemplates.getCompanyTemplate(templateId);
        if (companyTemplate == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "COMPANY_TEMPLATE_NOT_FOUND", "Company template not found");
        }
        return companyTemplate;
    }

    private RoleDefinitionConfig requireRoleDefinition(String roleId) {
        for (RoleDefinitionConfig definition : dbRoleDefinitions.getActiveRoleDefinitions()) {
            if (definition.getRoleId() != null && definition.getRoleId().equals(roleId)) {
                return definition;
            }
        }
        throw new ApiException(HttpStatus.NOT_FOUND, "ROLE_DEFINITION_NOT_FOUND", "Role definition not found");
    }

    private PlatformCapabilityConfig requireCapability(String capabilityKey) {
        PlatformCapabilityConfig capability = dbPlatformCapabilities.getCapability(capabilityKey == null ? null : capabilityKey.trim());
        if (capability == null || capability.getCapabilityKey() == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "CAPABILITY_NOT_FOUND", "Capability not found");
        }
        if (!"active".equalsIgnoreCase(capability.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CAPABILITY_INACTIVE", "Capability is not active");
        }
        return capability;
    }

    private void ensureBranchBelongsToTenant(int tenantId, int branchId) {
        Branch branch = dbBranch.getBranchById(branchId);
        if (branch.getBranchOfCompanyId() != tenantId) {
            throw new ApiException(HttpStatus.FORBIDDEN, "BRANCH_ACCESS_DENIED", "Branch does not belong to the resolved tenant");
        }
    }

    private void ensureUserBelongsToTenant(int tenantId, int userId) {
        List<ConfigurationAdminUserSummary> users = dbConfigurationAdmin.getUsersForTenant(tenantId, null);
        for (ConfigurationAdminUserSummary user : users) {
            if (user.getUserId() == userId) {
                return;
            }
        }
        throw new ApiException(HttpStatus.FORBIDDEN, "USER_ACCESS_DENIED", "User does not belong to the resolved tenant");
    }

    private ArrayList<String> extractRoleIds(List<RoleDefinitionConfig> roleDefinitions) {
        ArrayList<String> roleIds = new ArrayList<>();
        for (RoleDefinitionConfig definition : roleDefinitions) {
            if (definition.getRoleId() != null && !definition.getRoleId().trim().isEmpty()) {
                roleIds.add(definition.getRoleId().trim());
            }
        }
        return roleIds;
    }

    private String normalizeModuleMode(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "standard";
        }
        String normalized = value.trim().toLowerCase();
        if ("hidden".equals(normalized) || "read_only".equals(normalized) || "standard".equals(normalized)) {
            return normalized;
        }
        return "standard";
    }

    private String normalizeReason(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        String normalized = value.trim().toLowerCase().replace(' ', '_');
        return normalized.length() > 80 ? normalized.substring(0, 80) : normalized;
    }

    private String normalizeScopeType(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "company";
        }
        String normalized = value.trim().toLowerCase();
        if ("branch".equals(normalized) || "company".equals(normalized) || "self".equals(normalized)) {
            return normalized;
        }
        return "company";
    }

    private String normalizeGrantMode(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "allow";
        }
        String normalized = value.trim().toLowerCase();
        return "deny".equals(normalized) ? "deny" : "allow";
    }

    private ResolvedAdminContext resolveAdminContext(String authenticatedName, Integer requestedTenantId, Integer requestedBranchId) {
        String userName = extractBaseUserName(authenticatedName);
        User user = dbUsers.getUser(userName);
        if (user == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found");
        }

        Company company;
        if (requestedTenantId != null) {
            company = dbCompany.getCompanyById(requestedTenantId);
            if (company == null) {
                throw new ApiException(HttpStatus.NOT_FOUND, "TENANT_NOT_FOUND", "Tenant company not found");
            }
            if (!userBelongsToCompany(user, company)) {
                throw new ApiException(HttpStatus.FORBIDDEN, "TENANT_ACCESS_DENIED", "User does not belong to the requested tenant");
            }
        } else {
            company = resolveLegacyCompany(user);
        }

        Integer activeBranchId = requestedBranchId;
        if (activeBranchId != null && !branchBelongsToCompany(company, activeBranchId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "BRANCH_ACCESS_DENIED", "Branch does not belong to the resolved tenant");
        }

        return new ResolvedAdminContext(user, company.getCompanyId(), activeBranchId);
    }

    private Company resolveLegacyCompany(User user) {
        if (user.getBranchId() > 0) {
            Company company = dbCompany.getCompanyAndBranchesByUserName(user.getUserName());
            if (company != null) {
                return company;
            }
        }

        Company company = dbCompany.getCompanyByOwnerId(user.getUserId());
        if (company != null) {
            return company;
        }

        throw new ApiException(HttpStatus.NOT_FOUND, "TENANT_CONTEXT_NOT_FOUND", "Could not resolve tenant context for user");
    }

    private boolean userBelongsToCompany(User user, Company company) {
        if (company.getOwnerId() == user.getUserId()) {
            return true;
        }
        if (user.getBranchId() <= 0) {
            return false;
        }
        return branchBelongsToCompany(company, user.getBranchId());
    }

    private boolean branchBelongsToCompany(Company company, int branchId) {
        ArrayList<Branch> branches = company.getBranchList();
        if (branches == null) {
            return false;
        }
        for (Branch branch : branches) {
            if (branch.getBranchID() == branchId) {
                return true;
            }
        }
        return false;
    }

    private String extractBaseUserName(String value) {
        if (value != null && value.contains(" : ")) {
            return value.split(" : ")[0];
        }
        return value == null ? "" : value.trim();
    }

    private static class ResolvedAdminContext {
        private final User user;
        private final int tenantId;
        private final Integer activeBranchId;

        private ResolvedAdminContext(User user, int tenantId, Integer activeBranchId) {
            this.user = user;
            this.tenantId = tenantId;
            this.activeBranchId = activeBranchId;
        }
    }
}
