package com.example.valueinsoftbackend.ai.service;

public record AiModelRequest(
        String systemPrompt,
        String userMessage,
        String mode,
        String knowledgeContext,
        String conversationContext,
        String provider
) {
    public AiModelRequest(String systemPrompt, String userMessage, String mode, String knowledgeContext) {
        this(systemPrompt, userMessage, mode, knowledgeContext, "", null);
    }

    public AiModelRequest(String systemPrompt,
                          String userMessage,
                          String mode,
                          String knowledgeContext,
                          String conversationContext) {
        this(systemPrompt, userMessage, mode, knowledgeContext, conversationContext, null);
    }
}
