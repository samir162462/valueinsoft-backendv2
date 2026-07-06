package com.example.valueinsoftbackend.ai.memory;

import java.time.Instant;
import java.util.UUID;

public record AiUserMemoryRecord(
        UUID id,
        long companyId,
        long userId,
        String memoryKey,
        String memoryValue,
        String source,
        Instant createdAt,
        Instant updatedAt
) {
}
