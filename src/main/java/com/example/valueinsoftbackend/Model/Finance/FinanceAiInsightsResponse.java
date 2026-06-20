package com.example.valueinsoftbackend.Model.Finance;

import java.util.List;

public record FinanceAiInsightsResponse(
        String summary,
        String riskLevel,
        List<String> recommendedActions,
        List<FinanceAiFocusAccount> focusAccounts,
        boolean fallbackUsed,
        String modelName,
        String source,
        String generatedAt
) {
}
