package com.example.valueinsoftbackend.ai.memory;

import java.time.Instant;
import java.util.UUID;

public record AiMessageRecord(
        UUID id,
        UUID conversationId,
        long companyId,
        Long branchId,
        long userId,
        String role,
        String content,
        int tokenCount,
        Instant createdAt
) {
}
