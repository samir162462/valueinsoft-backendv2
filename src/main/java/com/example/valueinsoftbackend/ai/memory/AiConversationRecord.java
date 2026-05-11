package com.example.valueinsoftbackend.ai.memory;

import java.time.Instant;
import java.util.UUID;

public record AiConversationRecord(
        UUID id,
        long companyId,
        Long branchId,
        long userId,
        String mode,
        String title,
        Instant createdAt,
        Instant updatedAt,
        boolean deleted
) {
}
