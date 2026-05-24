package com.example.valueinsoftbackend.ai.dto;

public record AiDailyInsightDto(
        String id,
        String type,
        String title,
        String text,
        String action,
        String target,
        String params,
        Double confidence,
        String source
) {
}
