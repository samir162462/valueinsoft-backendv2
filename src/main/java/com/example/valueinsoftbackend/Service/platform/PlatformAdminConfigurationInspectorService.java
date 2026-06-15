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
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Branch;
import com.example.valueinsoftbackend.Model.Company;
import com.example.valueinsoftbackend.Model.Configuration.CompanyTemplateConfig;
import com.example.valueinsoftbackend.Model.Configuration.EffectiveConfiguration;
import com.example.valueinsoftbackend.Model.Configuration.EffectiveModuleConfig;
import com.example.valueinsoftbackend.Model.Configuration.PackagePlanConfig;
import com.example.valueinsoftbackend.Model.Configuration.ResolvedWorkflowFlag;
import com.example.valueinsoftbackend.Model.Configuration.RoleDefinitionConfig;
import com.example.valueinsoftbackend.Model.Configuration.RoleGrantConfig;
import com.example.valueinsoftbackend.Model.Configuration.TenantAdminPortalConfig;
import com.example.valueinsoftbackend.Model.Configuration.TenantConfig;
import com.example.valueinsoftbackend.Model.Configuration.TenantRoleAssignmentConfig;
import com.example.valueinsoftbackend.Model.Configuration.TenantUserGrantOverrideConfig;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformConfigAssignmentsResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class PlatformAdminConfigurationInspectorService {

    private final PlatformAuthorizationService platformAuthorizationService;
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

    public PlatformAdminConfigurationInspectorService(PlatformAuthorizationService platformAuthorizationService,
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
                                                      EffectiveConfigurationService effectiveConfigurationService) {
        this.platformAuthorizationService = platformAuthorizationService;
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
    }

    public TenantAdminPortalConfig getConfigurationPortalForAuthenticatedUser(String authenticatedName,
                                                                              int tenantId,
                                                                              Integer branchId) {
        platformAuthorizationService.requirePlatformCapability(authenticatedName, "platform.configuration.read");
        Company company = requireCompany(tenantId);
        if (branchId != null) {
            ensureBranchBelongsToTenant(tenantId, branchId);
        }

        TenantConfig tenant = requireTenant(tenantId);
        PackagePlanConfig packagePlan = requirePackagePlan(tenant.getPackageId());
        CompanyTemplateConfig companyTemplate = requireCompanyTemplate(tenant.getTemplateId());
        EffectiveConfiguration effectiveConfiguration = effectiveConfigurationService.getEffectiveConfiguration(
                tenantId,
                company.getOwnerId(),
                branchId
        );

        ArrayList<RoleDefinitionConfig> roleDefinitions = new ArrayList<>(dbRoleDefinitions.getActiveRoleDefinitions());
        ArrayList<RoleGrantConfig> roleGrants = new ArrayList<>(dbRoleGrants.getGrantsForRoleIds(extractRoleIds(roleDefinitions)));

        return new TenantAdminPortalConfig(
                tenantId,
                branchId,
                tenant,
                packagePlan,
                companyTemplate,
                new ArrayList<>(dbBranch.getBranchByCompanyId(tenantId)),
                new ArrayList<>(dbConfigurationAdmin.getUsersForTenant(tenantId, branchId)),
                roleDefinitions,
                roleGrants,
                new ArrayList<>(dbConfigurationAdmin.getTenantRoleAssignments(tenantId, branchId)),
                new ArrayList<>(dbConfigurationAdmin.getTenantUserGrantOverrides(tenantId, branchId)),
                new ArrayList<>(dbPlatformModules.getAllModules()),
                new ArrayList<>(dbPlatformCapabilities.getActiveCapabilities()),
                effectiveConfiguration.getModules(),
                new ArrayList<>(dbTenants.getTenantModuleOverrides(tenantId)),
                effectiveConfiguration.getWorkflowFlags(),
                new ArrayList<>(dbTenants.getTenantWorkflowOverrides(tenantId))
        );
    }

    public ArrayList<EffectiveModuleConfig> getModulesForAuthenticatedUser(String authenticatedName,
                                                                           int tenantId,
                                                                           Integer branchId) {
        return getConfigurationPortalForAuthenticatedUser(authenticatedName, tenantId, branchId).getEffectiveModules();
    }

    public ArrayList<ResolvedWorkflowFlag> getWorkflowFlagsForAuthenticatedUser(String authenticatedName,
                                                                                int tenantId,
                                                                                Integer branchId) {
        return getConfigurationPortalForAuthenticatedUser(authenticatedName, tenantId, branchId).getEffectiveWorkflowFlags();
    }

    public PlatformConfigAssignmentsResponse getAssignmentsForAuthenticatedUser(String authenticatedName,
                                                                                int tenantId,
                                                                                Integer branchId) {
        TenantAdminPortalConfig portal = getConfigurationPortalForAuthenticatedUser(authenticatedName, tenantId, branchId);
        return new PlatformConfigAssignmentsResponse(
                portal.getRoleDefinitions(),
                portal.getRoleGrants(),
                portal.getRoleAssignments()
        );
    }

    public ArrayList<TenantUserGrantOverrideConfig> getUserOverridesForAuthenticatedUser(String authenticatedName,
                                                                                          int tenantId,
                                                                                          Integer branchId) {
        return getConfigurationPortalForAuthenticatedUser(authenticatedName, tenantId, branchId).getUserGrantOverrides();
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

    private Company requireCompany(int tenantId) {
        Company company = dbCompany.getCompanyById(tenantId);
        if (company == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "COMPANY_NOT_FOUND", "Company not found");
        }
        return company;
    }

    private void ensureBranchBelongsToTenant(int tenantId, int branchId) {
        Branch branch = dbBranch.getBranchById(branchId);
        if (branch.getBranchOfCompanyId() != tenantId) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BRANCH_SCOPE_INVALID", "Branch does not belong to the requested tenant");
        }
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
}
