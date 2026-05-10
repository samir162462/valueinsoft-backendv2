package com.example.valueinsoftbackend.ai.dto;

import java.time.Instant;

public record AiMessageDto(
        String id,
        String conversationId,
        String role,
        String content,
        Instant createdAt
) {
}
