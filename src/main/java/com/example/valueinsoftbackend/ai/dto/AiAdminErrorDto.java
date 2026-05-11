package com.example.valueinsoftbackend.ai.dto;

import java.time.Instant;
import java.util.UUID;

public record AiAdminErrorDto(
        UUID id,
        UUID conversationId,
        long companyId,
        Long branchId,
        long userId,
        String source,
        String message,
        Long durationMs,
        Instant createdAt
) {
}
