package com.example.valueinsoftbackend.pricing.dynamic.model;

public record ProductPricingScope(
        long productId,
        String category,
        String businessLineKey,
        String pricingPolicyCode
) {
}
