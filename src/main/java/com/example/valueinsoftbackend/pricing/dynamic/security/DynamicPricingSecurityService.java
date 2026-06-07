package com.example.valueinsoftbackend.pricing.dynamic.security;

import com.example.valueinsoftbackend.Service.AuthorizationService;
import org.springframework.stereotype.Service;

@Service
public class DynamicPricingSecurityService {

    private final AuthorizationService authorizationService;

    public DynamicPricingSecurityService(AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    public void requireView(String actorName, int companyId, int branchId) {
        authorizationService.assertAuthenticatedCapability(actorName, companyId, branchId, "inventory.pricing.view");
    }

    public boolean canReadCost(String actorName, int companyId, int branchId) {
        return authorizationService.hasAuthenticatedCapability(actorName, companyId, branchId, "inventory.pricing.cost.read");
    }

    public void requirePolicyManage(String actorName, int companyId, Integer branchId) {
        authorizationService.assertAuthenticatedCapability(actorName, companyId, branchId, "inventory.pricing.policy.manage");
    }

    public void requireRecommendationRun(String actorName, int companyId, int branchId) {
        authorizationService.assertAuthenticatedCapability(actorName, companyId, branchId, "inventory.pricing.recommendation.run");
    }

    public void requireAdjustmentPreview(String actorName, int companyId, int branchId) {
        authorizationService.assertAuthenticatedCapability(actorName, companyId, branchId, "inventory.pricing.adjustment.preview");
    }

    public void requireAdjustmentCreate(String actorName, int companyId, int branchId) {
        authorizationService.assertAuthenticatedCapability(actorName, companyId, branchId, "inventory.pricing.adjustment.create");
    }

    public void requireAdjustmentSubmit(String actorName, int companyId, int branchId) {
        authorizationService.assertAuthenticatedCapability(actorName, companyId, branchId, "inventory.pricing.adjustment.submit");
    }

    public void requireAdjustmentApprove(String actorName, int companyId, int branchId) {
        authorizationService.assertAuthenticatedCapability(actorName, companyId, branchId, "inventory.pricing.adjustment.approve");
    }

    public void requireAdjustmentApply(String actorName, int companyId, int branchId) {
        authorizationService.assertAuthenticatedCapability(actorName, companyId, branchId, "inventory.pricing.adjustment.apply");
    }
}
