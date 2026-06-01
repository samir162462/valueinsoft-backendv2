package com.example.valueinsoftbackend.ai.dto;

public record AiKnowledgeRuntimeStatusDto(
        boolean ragEnabled,
        boolean embeddingEnabled,
        String embeddingProvider,
        String embeddingModel,
        int embeddingDimension,
        boolean embeddingApiKeyConfigured,
        String ingestionStatus,
        String ingestionMessage
) {
}
