package com.example.valueinsoftbackend.ai.service;

public record AiModelRequest(
        String systemPrompt,
        String userMessage,
        String mode,
        String knowledgeContext,
        String conversationContext
) {
    public AiModelRequest(String systemPrompt, String userMessage, String mode, String knowledgeContext) {
        this(systemPrompt, userMessage, mode, knowledgeContext, "");
    }
}
