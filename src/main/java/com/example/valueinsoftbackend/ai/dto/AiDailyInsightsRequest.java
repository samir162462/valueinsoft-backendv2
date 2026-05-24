package com.example.valueinsoftbackend.ai.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record AiDailyInsightsRequest(
        @NotNull(message = "Branch is required")
        Long branchId,
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "Date must use yyyy-MM-dd")
        String date,
        String locale,
        @Min(1)
        @Max(5)
        Integer maxInsights,
        Boolean forceRefresh
) {
    public int resolvedMaxInsights() {
        return maxInsights == null ? 5 : Math.max(1, Math.min(5, maxInsights));
    }

    public boolean shouldForceRefresh() {
        return Boolean.TRUE.equals(forceRefresh);
    }
}
