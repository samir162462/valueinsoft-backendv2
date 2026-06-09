package com.example.valueinsoftbackend.fx.model;

public record FxCompanyProcessingResult(
        int companyId,
        String status,
        int evaluatedProducts,
        int recommendationRuns,
        int recommendationsGenerated
) {
}
