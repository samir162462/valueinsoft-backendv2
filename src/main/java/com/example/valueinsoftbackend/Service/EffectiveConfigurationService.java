package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbCompanyTemplates;
import com.example.valueinsoftbackend.DatabaseRequests.DbPackagePlans;
import com.example.valueinsoftbackend.DatabaseRequests.DbPlatformModules;
import com.example.valueinsoftbackend.DatabaseRequests.DbRoleGrants;
import com.example.valueinsoftbackend.DatabaseRequests.DbTenantRoleAssignments;
import com.example.valueinsoftbackend.DatabaseRequests.DbTenantUserGrantOverrides;
import com.example.valueinsoftbackend.DatabaseRequests.DbTenants;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Configuration.CompanyTemplateConfig;
import com.example.valueinsoftbackend.Model.Configuration.CompanyTemplateModuleDefault;
import com.example.valueinsoftbackend.Model.Configuration.CompanyTemplateWorkflowDefault;
import com.example.valueinsoftbackend.Model.Configuration.EffectiveConfiguration;
import com.example.valueinsoftbackend.Model.Configuration.EffectiveModuleConfig;
import com.example.valueinsoftbackend.Model.Configuration.NavigationItemConfig;
import com.example.valueinsoftbackend.Model.Configuration.OnboardingStateConfig;
import com.example.valueinsoftbackend.Model.Configuration.PackageModulePolicy;
import com.example.valueinsoftbackend.Model.Configuration.PackagePlanConfig;
import com.example.valueinsoftbackend.Model.Configuration.PlatformModuleConfig;
import com.example.valueinsoftbackend.Model.Configuration.ResolvedCapabilityConfig;
import com.example.valueinsoftbackend.Model.Configuration.ResolvedWorkflowFlag;
import com.example.valueinsoftbackend.Model.Configuration.RoleGrantConfig;
import com.example.valueinsoftbackend.Model.Configuration.TenantConfig;
import com.example.valueinsoftbackend.Model.Configuration.TenantModuleOverrideConfig;
import com.example.valueinsoftbackend.Model.Configuration.TenantRoleAssignmentConfig;
import com.example.valueinsoftbackend.Model.Configuration.TenantUserGrantOverrideConfig;
import com.example.valueinsoftbackend.Model.Configuration.TenantWorkflowOverrideConfig;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Aggregates tenant, package, template, role, and override data into one effective
 * configuration payload that the frontend and future authorization layers can consume.
 */
@Service
public class EffectiveConfigurationService {

    private final DbPlatformModules dbPlatformModules;
    private final DbPackagePlans dbPackagePlans;
    private final DbCompanyTemplates dbCompanyTemplates;
    private final DbTenants dbTenants;
    private final DbTenantRoleAssignments dbTenantRoleAssignments;
    private final DbTenantUserGrantOverrides dbTenantUserGrantOverrides;
    private final DbRoleGrants dbRoleGrants;

    public EffectiveConfigurationService(DbPlatformModules dbPlatformModules,
                                         DbPackagePlans dbPackagePlans,
                                         DbCompanyTemplates dbCompanyTemplates,
                                         DbTenants dbTenants,
                                         DbTenantRoleAssignments dbTenantRoleAssignments,
                                         DbTenantUserGrantOverrides dbTenantUserGrantOverrides,
                                         DbRoleGrants dbRoleGrants) {
        this.dbPlatformModules = dbPlatformModules;
        this.dbPackagePlans = dbPackagePlans;
        this.dbCompanyTemplates = dbCompanyTemplates;
        this.dbTenants = dbTenants;
        this.dbTenantRoleAssignments = dbTenantRoleAssignments;
        this.dbTenantUserGrantOverrides = dbTenantUserGrantOverrides;
        this.dbRoleGrants = dbRoleGrants;
    }

    /**
     * Resolves the effective configuration for a specific user inside a tenant context.
     *
     * @param tenantId tenant identifier anchored to legacy {@code Company.id}
     * @param userId authenticated user identifier from {@code public.users}
     * @param activeBranchId optional active branch used by future scope-aware resolution
     * @return aggregated configuration payload for the tenant and user
     */
    public EffectiveConfiguration getEffectiveConfiguration(int tenantId, int userId, Integer activeBranchId) {
        TenantConfig tenant = requireTenant(tenantId);
        PackagePlanConfig packagePlan = requirePackagePlan(tenant.getPackageId());
        CompanyTemplateConfig companyTemplate = requireCompanyTemplate(tenant.getTemplateId());
        OnboardingStateConfig onboardingState = dbTenants.getOnboardingState(tenantId);

        List<PlatformModuleConfig> platformModules = dbPlatformModules.getAllModules();
        List<PackageModulePolicy> packageModulePolicies = dbPackagePlans.getPackageModulePolicies(packagePlan.getPackageId());
        List<CompanyTemplateModuleDefault> templateModuleDefaults = dbCompanyTemplates.getTemplateModuleDefaults(companyTemplate.getTemplateId());
        List<CompanyTemplateWorkflowDefault> templateWorkflowDefaults = dbCompanyTemplates.getTemplateWorkflowDefaults(companyTemplate.getTemplateId());
        List<TenantModuleOverrideConfig> tenantModuleOverrides = dbTenants.getTenantModuleOverrides(tenantId);
        List<TenantWorkflowOverrideConfig> tenantWorkflowOverrides = dbTenants.getTenantWorkflowOverrides(tenantId);
        List<TenantRoleAssignmentConfig> roleAssignments = dbTenantRoleAssignments.getUserTenantRoleAssignments(tenantId, userId);
        List<TenantUserGrantOverrideConfig> userGrantOverrides = dbTenantUserGrantOverrides.getUserGrantOverrides(tenantId, userId);
        List<RoleGrantConfig> roleGrants = dbRoleGrants.getGrantsForRoleIds(extractRoleIds(roleAssignments));

        return new EffectiveConfiguration(
                tenant,
                packagePlan,
                companyTemplate,
                onboardingState,
                activeBranchId,
                resolveModules(platformModules, packageModulePolicies, templateModuleDefaults, tenantModuleOverrides),
                resolveWorkflowFlags(templateWorkflowDefaults, tenantWorkflowOverrides),
                new ArrayList<>(roleAssignments),
                new ArrayList<>(roleGrants),
                new ArrayList<>(userGrantOverrides),
                resolveCapabilities(roleAssignments, roleGrants, userGrantOverrides, activeBranchId)
        );
    }

    /**
     * Resolves configuration without an explicit branch override.
     */
    public EffectiveConfiguration getEffectiveConfiguration(int tenantId, int userId) {
        return getEffectiveConfiguration(tenantId, userId, null);
    }

    /**
     * Resolves only the effective capabilities for a specific user and tenant context.
     */
    public ArrayList<ResolvedCapabilityConfig> getEffectiveCapabilities(int tenantId, int userId, Integer activeBranchId) {
        TenantConfig tenant = requireTenant(tenantId);
        List<TenantRoleAssignmentConfig> roleAssignments = dbTenantRoleAssignments.getUserTenantRoleAssignments(tenant.getTenantId(), userId);
        List<TenantUserGrantOverrideConfig> userGrantOverrides = dbTenantUserGrantOverrides.getUserGrantOverrides(tenant.getTenantId(), userId);
        List<RoleGrantConfig> roleGrants = dbRoleGrants.getGrantsForRoleIds(extractRoleIds(roleAssignments));
        return resolveCapabilities(roleAssignments, roleGrants, userGrantOverrides, activeBranchId);
    }

    /**
     * Projects enabled modules into a lightweight navigation response for the frontend shell.
     */
    public ArrayList<NavigationItemConfig> getNavigationItems(int tenantId, int userId, Integer activeBranchId) {
        EffectiveConfiguration configuration = getEffectiveConfiguration(tenantId, userId, activeBranchId);
        ArrayList<NavigationItemConfig> navigationItems = new ArrayList<>();

        for (EffectiveModuleConfig module : configuration.getModules()) {
            if (!module.isEnabled()) {
                continue;
            }
            navigationItems.add(
                    new NavigationItemConfig(
                            module.getModuleId(),
                            module.getDisplayName(),
                            module.getCategory(),
                            module.getMode(),
                            module.getSource()
                    )
            );
        }

        return navigationItems;
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

    private ArrayList<String> extractRoleIds(List<TenantRoleAssignmentConfig> assignments) {
        ArrayList<String> roleIds = new ArrayList<>();
        for (TenantRoleAssignmentConfig assignment : assignments) {
            if (assignment.getRoleId() != null && !assignment.getRoleId().trim().isEmpty()) {
                roleIds.add(assignment.getRoleId().trim());
            }
        }
        return roleIds;
    }

    /**
     * Applies platform defaults, package policies, template defaults, and tenant overrides
     * in order to determine the current module state.
     */
    private ArrayList<EffectiveModuleConfig> resolveModules(List<PlatformModuleConfig> platformModules,
                                                            List<PackageModulePolicy> packageModulePolicies,
                                                            List<CompanyTemplateModuleDefault> templateModuleDefaults,
                                                            List<TenantModuleOverrideConfig> tenantModuleOverrides) {
        Map<String, EffectiveModuleConfig> resolved = new LinkedHashMap<>();
        Map<String, PackageModulePolicy> packagePoliciesByModule = new LinkedHashMap<>();

        for (PlatformModuleConfig module : platformModules) {
            resolved.put(
                    module.getModuleId(),
                    new EffectiveModuleConfig(
                            module.getModuleId(),
                            module.getDisplayName(),
                            module.getCategory(),
                            module.isDefaultEnabled(),
                            "platform_default",
                            null
                    )
            );
        }

        for (PackageModulePolicy policy : packageModulePolicies) {
            packagePoliciesByModule.put(policy.getModuleId(), policy);
            EffectiveModuleConfig module = resolved.get(policy.getModuleId());
            if (module != null) {
                module.setEnabled(policy.isEnabled());
                module.setSource("package");
                if (hasText(policy.getMode())) {
                    module.setMode(policy.getMode());
                }
            }
        }

        for (CompanyTemplateModuleDefault templateDefault : templateModuleDefaults) {
            EffectiveModuleConfig module = resolved.get(templateDefault.getModuleId());
            if (module == null) {
                continue;
            }
            PackageModulePolicy packagePolicy = packagePoliciesByModule.get(templateDefault.getModuleId());
            if (templateDefault.isEnabled() && packagePolicy != null && !packagePolicy.isEnabled()) {
                module.setEnabled(false);
                module.setSource("package_locked");
            } else {
                module.setEnabled(templateDefault.isEnabled());
                module.setSource("template");
            }
            if (hasText(templateDefault.getMode())) {
                module.setMode(templateDefault.getMode());
            }
        }

        for (TenantModuleOverrideConfig override : tenantModuleOverrides) {
            EffectiveModuleConfig module = resolved.get(override.getModuleId());
            if (module == null) {
                continue;
            }
            PackageModulePolicy packagePolicy = packagePoliciesByModule.get(override.getModuleId());
            if (override.isEnabled() && packagePolicy != null && !packagePolicy.isEnabled()) {
                module.setEnabled(false);
                module.setSource("package_locked");
            } else {
                module.setEnabled(override.isEnabled());
                module.setSource("tenant_override");
            }
            if (hasText(override.getMode())) {
                module.setMode(override.getMode());
            }
        }

        return new ArrayList<>(resolved.values());
    }

    /**
     * Applies template workflow defaults followed by tenant workflow overrides.
     */
    private ArrayList<ResolvedWorkflowFlag> resolveWorkflowFlags(List<CompanyTemplateWorkflowDefault> templateWorkflowDefaults,
                                                                 List<TenantWorkflowOverrideConfig> tenantWorkflowOverrides) {
        Map<String, ResolvedWorkflowFlag> resolved = new LinkedHashMap<>();

        for (CompanyTemplateWorkflowDefault workflowDefault : templateWorkflowDefaults) {
            resolved.put(
                    workflowDefault.getFlagKey(),
                    new ResolvedWorkflowFlag(
                            workflowDefault.getFlagKey(),
                            workflowDefault.getFlagValueJson(),
                            "template"
                    )
            );
        }

        for (TenantWorkflowOverrideConfig override : tenantWorkflowOverrides) {
            resolved.put(
                    override.getFlagKey(),
                    new ResolvedWorkflowFlag(
                            override.getFlagKey(),
                            override.getFlagValueJson(),
                            "tenant_override"
                    )
            );
        }

        return new ArrayList<>(resolved.values());
    }

    /**
     * Resolves the currently effective capabilities from role assignments, role grants,
     * and user-specific overrides for the selected branch context.
     */
    private ArrayList<ResolvedCapabilityConfig> resolveCapabilities(List<TenantRoleAssignmentConfig> roleAssignments,
                                                                    List<RoleGrantConfig> roleGrants,
                                                                    List<TenantUserGrantOverrideConfig> userGrantOverrides,
                                                                    Integer activeBranchId) {
        Map<String, ResolvedCapabilityConfig> resolved = new LinkedHashMap<>();
        Map<String, List<RoleGrantConfig>> roleGrantsByRoleId = new LinkedHashMap<>();

        for (RoleGrantConfig roleGrant : roleGrants) {
            roleGrantsByRoleId.computeIfAbsent(roleGrant.getRoleId(), key -> new ArrayList<>()).add(roleGrant);
        }

        for (TenantRoleAssignmentConfig assignment : roleAssignments) {
            if (!assignmentAppliesToBranch(assignment, activeBranchId)) {
                continue;
            }

            List<RoleGrantConfig> grantsForRole = roleGrantsByRoleId.get(assignment.getRoleId());
            if (grantsForRole == null || grantsForRole.isEmpty()) {
                continue;
            }

            for (RoleGrantConfig roleGrant : grantsForRole) {
                ResolvedScope resolvedScope = resolveScopeForAssignment(roleGrant, assignment);
                if (resolvedScope == null || !scopeAppliesToBranch(resolvedScope.scopeType, resolvedScope.scopeBranchId, activeBranchId)) {
                    continue;
                }

                ResolvedCapabilityConfig capability = new ResolvedCapabilityConfig(
                        roleGrant.getCapabilityKey(),
                        roleGrant.getGrantMode(),
                        resolvedScope.scopeType,
                        resolvedScope.scopeBranchId,
                        "role_grant",
                        assignment.getRoleId(),
                        assignment.getAssignmentId(),
                        null
                );
                resolved.put(capabilityMapKey(capability.getCapabilityKey(), capability.getScopeType(), capability.getScopeBranchId()), capability);
            }
        }

        for (TenantUserGrantOverrideConfig override : userGrantOverrides) {
            if (!scopeAppliesToBranch(override.getScopeType(), override.getScopeBranchId(), activeBranchId)) {
                continue;
            }

            String key = capabilityMapKey(override.getCapabilityKey(), override.getScopeType(), override.getScopeBranchId());
            if ("deny".equalsIgnoreCase(override.getGrantMode())) {
                resolved.remove(key);
                continue;
            }

            resolved.put(
                    key,
                    new ResolvedCapabilityConfig(
                            override.getCapabilityKey(),
                            override.getGrantMode(),
                            override.getScopeType(),
                            override.getScopeBranchId(),
                            "user_override",
                            null,
                            null,
                            override.getOverrideId()
                    )
            );
        }

        return new ArrayList<>(resolved.values());
    }

    private boolean assignmentAppliesToBranch(TenantRoleAssignmentConfig assignment, Integer activeBranchId) {
        if ("branch".equalsIgnoreCase(assignment.getScopeType())) {
            return activeBranchId != null && Objects.equals(activeBranchId, assignment.getScopeBranchId());
        }
        return true;
    }

    private boolean scopeAppliesToBranch(String scopeType, Integer scopeBranchId, Integer activeBranchId) {
        if ("branch".equalsIgnoreCase(scopeType)) {
            return activeBranchId != null && Objects.equals(activeBranchId, scopeBranchId);
        }
        return true;
    }

    private ResolvedScope resolveScopeForAssignment(RoleGrantConfig roleGrant, TenantRoleAssignmentConfig assignment) {
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

    private String capabilityMapKey(String capabilityKey, String scopeType, Integer scopeBranchId) {
        return capabilityKey + "|" + scopeType + "|" + (scopeBranchId == null ? "null" : scopeBranchId);
    }

    private static class ResolvedScope {
        private final String scopeType;
        private final Integer scopeBranchId;

        private ResolvedScope(String scopeType, Integer scopeBranchId) {
            this.scopeType = scopeType;
            this.scopeBranchId = scopeBranchId;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
