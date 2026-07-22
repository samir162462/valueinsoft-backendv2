package com.example.valueinsoftbackend.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AiChatRequest(
        @Size(max = 36, message = "Conversation id is invalid")
        String conversationId,
        @Pattern(
                regexp = "(?i)HELP|BUSINESS|SALES|INVENTORY|SUPPLIERS|CUSTOMERS|SHIFT|ADMIN",
                message = "AI mode is not available"
        )
        String mode,
        @NotBlank(message = "Message is required")
        @Size(max = 2000, message = "Message is too long")
        String message,
        Long branchId,
        Boolean realAiOnly,
        @Pattern(regexp = "(?i)deepseek|gemini", message = "AI provider is not available")
        String provider
) {
    public boolean useRealAiOnly() {
        // Retained in the JSON contract for older clients. Real-model answers
        // are now a server invariant, even if the flag is absent or false.
        return true;
    }
}
