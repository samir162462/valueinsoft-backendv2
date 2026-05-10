package com.example.valueinsoftbackend.ai.service;

public record AiModelRequest(
        String systemPrompt,
        String userMessage,
        String mode
) {
}
