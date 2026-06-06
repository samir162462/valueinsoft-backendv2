package com.example.valueinsoftbackend.customerbehavior.dto;

import java.util.List;

public record CustomerBehaviorInsight(
        String summary,
        List<String> keyPatterns,
        List<String> prioritySegments,
        List<String> recommendedActions,
        double confidence,
        List<String> warnings,
        List<String> dataGaps,
        String source,
        boolean cached,
        String generatedAt,
        String modelName,
        boolean fallbackUsed
) {
}
