package com.example.valueinsoftbackend.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiChatRequest(
        String conversationId,
        String mode,
        @NotBlank(message = "Message is required")
        @Size(max = 2000, message = "Message is too long")
        String message,
        Long branchId,
        Boolean realAiOnly,
        String provider
) {
    public boolean useRealAiOnly() {
        return true;
    }
}
