package com.example.valueinsoftbackend.pricing.dynamic.service;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.pricing.dynamic.dto.DynamicPricingPolicyRequest;
import com.example.valueinsoftbackend.pricing.dynamic.dto.DynamicPricingPolicyResponse;
import com.example.valueinsoftbackend.pricing.dynamic.dto.EffectivePricingPolicyResponse;
import com.example.valueinsoftbackend.pricing.dynamic.model.DynamicPricingPolicy;
import com.example.valueinsoftbackend.pricing.dynamic.repository.DynamicPricingPolicyRepository;
import com.example.valueinsoftbackend.pricing.dynamic.security.DynamicPricingSecurityService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class DynamicPricingPolicyService {

    private final DynamicPricingPolicyRepository repository;
    private final DynamicPricingSecurityService securityService;
    private final PricingAuditService auditService;

    public DynamicPricingPolicyService(DynamicPricingPolicyRepository repository,
                                       DynamicPricingSecurityService securityService,
                                       PricingAuditService auditService) {
        this.repository = repository;
        this.securityService = securityService;
        this.auditService = auditService;
    }

    public EffectivePricingPolicyResponse effectivePolicy(String actorName, int companyId, int branchId, Long productId) {
        securityService.requireView(actorName, companyId, branchId);
        DynamicPricingPolicy policy = repository.findEffectivePolicy(companyId, branchId, productId)
                .orElseGet(() -> repository.systemDefaultPolicy(companyId, branchId));

        boolean systemDefault = policy.policyId() == null;
        return new EffectivePricingPolicyResponse(
                DynamicPricingPolicyResponse.from(policy),
                systemDefault ? "SYSTEM_DEFAULT" : policy.scopeType(),
                systemDefault ? null : policy.scopeValue(),
                systemDefault
        );
    }

    public DynamicPricingPolicyResponse savePolicy(String actorName, DynamicPricingPolicyRequest request) {
        int companyId = request.companyId().intValue();
        Integer branchId = request.branchId() == null ? null : request.branchId().intValue();
        securityService.requirePolicyManage(actorName, companyId, branchId);
        validatePolicy(request);

        DynamicPricingPolicy saved = repository.save(actorName, request);
        auditService.log(
                companyId,
                branchId,
                "PRICING_POLICY_SAVED",
                "POLICY",
                String.valueOf(saved.policyId()),
                actorName,
                "Dynamic pricing policy saved",
                "{\"scopeType\":\"" + saved.scopeType() + "\"}"
        );
        return DynamicPricingPolicyResponse.from(saved);
    }

    public java.util.List<DynamicPricingPolicyResponse> listPolicies(String actorName, int companyId, Integer branchId) {
        securityService.requireView(actorName, companyId, branchId != null ? branchId : 0);
        return repository.findAll(companyId, branchId).stream()
                .map(DynamicPricingPolicyResponse::from)
                .toList();
    }

    public DynamicPricingPolicyResponse getPolicy(String actorName, int companyId, long policyId) {
        DynamicPricingPolicy policy = repository.findById(companyId, policyId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PRICING_POLICY_NOT_FOUND", "Pricing policy not found"));
        securityService.requireView(actorName, companyId, policy.branchId() != null ? policy.branchId().intValue() : 0);
        return DynamicPricingPolicyResponse.from(policy);
    }

    public void deletePolicy(String actorName, int companyId, long policyId) {
        DynamicPricingPolicy policy = repository.findById(companyId, policyId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PRICING_POLICY_NOT_FOUND", "Pricing policy not found"));
        securityService.requirePolicyManage(actorName, companyId, policy.branchId() != null ? policy.branchId().intValue() : 0);
        repository.delete(companyId, policyId);
        auditService.log(
                companyId,
                policy.branchId() != null ? policy.branchId().intValue() : null,
                "PRICING_POLICY_DELETED",
                "POLICY",
                String.valueOf(policyId),
                actorName,
                "Dynamic pricing policy deleted",
                "{\"displayName\":\"" + policy.displayName() + "\"}"
        );
    }

    private void validatePolicy(DynamicPricingPolicyRequest request) {
        if (request.targetMarginPct().compareTo(request.minMarginPct()) < 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "PRICING_POLICY_MARGIN_ORDER_INVALID",
                    "targetMarginPct must be greater than or equal to minMarginPct"
            );
        }
        if (request.minFinalPrice() != null && request.maxFinalPrice() != null
                && request.maxFinalPrice().compareTo(request.minFinalPrice()) < 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "PRICING_POLICY_PRICE_RANGE_INVALID",
                    "maxFinalPrice must be greater than or equal to minFinalPrice"
            );
        }
        int slowDays = request.slowMovingDays() == null ? 45 : request.slowMovingDays();
        int deadDays = request.deadStockDays() == null ? 120 : request.deadStockDays();
        if (deadDays < slowDays) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "PRICING_POLICY_MOVEMENT_DAYS_INVALID",
                    "deadStockDays must be greater than or equal to slowMovingDays"
            );
        }
        BigDecimal lowCover = request.lowStockDaysCover() == null ? new BigDecimal("7.0000") : request.lowStockDaysCover();
        BigDecimal overstockCover = request.overstockDaysCover() == null ? new BigDecimal("60.0000") : request.overstockDaysCover();
        if (overstockCover.compareTo(lowCover) < 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "PRICING_POLICY_STOCK_COVER_INVALID",
                    "overstockDaysCover must be greater than or equal to lowStockDaysCover"
            );
        }
    }
}
