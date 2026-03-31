package com.example.valueinsoftbackend.Model.Configuration;

import com.example.valueinsoftbackend.Model.Branch;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TenantAdminPortalConfig {
    private int tenantId;
    private Integer activeBranchId;
    private TenantConfig tenant;
    private PackagePlanConfig packagePlan;
    private CompanyTemplateConfig companyTemplate;
    private ArrayList<Branch> branches;
    private ArrayList<ConfigurationAdminUserSummary> users;
    private ArrayList<RoleDefinitionConfig> roleDefinitions;
    private ArrayList<RoleGrantConfig> roleGrants;
    private ArrayList<TenantRoleAssignmentConfig> roleAssignments;
    private ArrayList<TenantUserGrantOverrideConfig> userGrantOverrides;
    private ArrayList<PlatformModuleConfig> platformModules;
    private ArrayList<PlatformCapabilityConfig> platformCapabilities;
    private ArrayList<EffectiveModuleConfig> effectiveModules;
    private ArrayList<TenantModuleOverrideConfig> moduleOverrides;
    private ArrayList<ResolvedWorkflowFlag> effectiveWorkflowFlags;
    private ArrayList<TenantWorkflowOverrideConfig> workflowOverrides;
}
