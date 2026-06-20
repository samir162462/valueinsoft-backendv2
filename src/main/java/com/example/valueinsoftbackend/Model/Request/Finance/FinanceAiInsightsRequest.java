package com.example.valueinsoftbackend.Model.Request.Finance;

import java.util.Map;
import java.util.UUID;

public record FinanceAiInsightsRequest(
        int companyId,
        UUID fiscalPeriodId,
        Integer branchId,
        String currencyCode,
        String reportType,
        Map<String, Object> reportSnapshot,
        String locale,
        boolean forceRefresh
) {
}
