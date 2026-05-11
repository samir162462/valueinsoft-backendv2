package com.example.valueinsoftbackend.ai.dto;

import java.time.Instant;

public record AiAdminTopQuestionDto(
        String question,
        long count,
        Instant lastAskedAt
) {
}
