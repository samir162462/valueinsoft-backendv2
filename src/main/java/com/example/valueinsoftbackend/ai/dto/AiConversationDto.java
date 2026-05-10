package com.example.valueinsoftbackend.ai.dto;

import java.time.Instant;
import java.util.List;

public record AiConversationDto(
        String id,
        String mode,
        String title,
        Long branchId,
        Instant createdAt,
        Instant updatedAt,
        List<AiMessageDto> messages
) {
}
