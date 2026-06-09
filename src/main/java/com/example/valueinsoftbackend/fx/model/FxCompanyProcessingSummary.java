package com.example.valueinsoftbackend.fx.model;

public record FxCompanyProcessingSummary(
        int totalActiveCompanies,
        int eligibleCompanies,
        int successfullyProcessedCompanies,
        int failedCompanies,
        int skippedCompanies,
        int totalEvaluatedProducts,
        int totalRecommendationsGenerated
) {
    public static FxCompanyProcessingSummary empty() {
        return new FxCompanyProcessingSummary(0, 0, 0, 0, 0, 0, 0);
    }
}
