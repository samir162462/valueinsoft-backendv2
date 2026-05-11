package com.example.valueinsoftbackend.ai.dto;

import java.time.Instant;
import java.util.UUID;

public record AiAdminToolAuditItemDto(
        UUID id,
        UUID conversationId,
        long companyId,
        Long branchId,
        long userId,
        String toolName,
        String outputSummary,
        boolean success,
        String errorMessage,
        Long durationMs,
        Instant createdAt
) {
}
