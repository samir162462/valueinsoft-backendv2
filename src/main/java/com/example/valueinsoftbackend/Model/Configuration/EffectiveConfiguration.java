package com.example.valueinsoftbackend.Model.Configuration;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EffectiveConfiguration {
    private TenantConfig tenant;
    private PackagePlanConfig packagePlan;
    private CompanyTemplateConfig companyTemplate;
    private OnboardingStateConfig onboardingState;
    private Integer activeBranchId;
    private ArrayList<EffectiveModuleConfig> modules;
    private ArrayList<ResolvedWorkflowFlag> workflowFlags;
    private ArrayList<TenantRoleAssignmentConfig> roleAssignments;
    private ArrayList<RoleGrantConfig> roleGrants;
    private ArrayList<TenantUserGrantOverrideConfig> userGrantOverrides;
    private ArrayList<ResolvedCapabilityConfig> resolvedCapabilities;
}
