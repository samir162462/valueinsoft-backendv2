package com.example.valueinsoftbackend.pricing.dynamic.dto;

public record EffectivePricingPolicyResponse(
        DynamicPricingPolicyResponse policy,
        String matchedScopeType,
        String matchedScopeValue,
        boolean systemDefault
) {
}
