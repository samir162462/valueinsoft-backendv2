package com.example.valueinsoftbackend.ai.dto;

import java.util.List;

public record AiChatResponse(
        String conversationId,
        String answer,
        String mode,
        List<String> suggestedQuestions,
        List<AiActionDto> actions,
        List<AiSourceDto> sources,
        List<AiToolCallDto> toolCalls,
        String providerName,
        String providerCode
) {
    public AiChatResponse(String conversationId,
                          String answer,
                          String mode,
                          List<String> suggestedQuestions,
                          List<AiActionDto> actions,
                          List<AiSourceDto> sources,
                          List<AiToolCallDto> toolCalls) {
        this(conversationId, answer, mode, suggestedQuestions, actions, sources, toolCalls, null, null);
    }
}
