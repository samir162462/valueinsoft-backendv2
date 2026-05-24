package com.example.valueinsoftbackend.ai.dto;

import java.util.List;

public record AiDailyInsightsResponse(
        List<AiDailyInsightDto> insights,
        String source,
        String generatedAt,
        String date,
        String model,
        boolean cached
) {
}
