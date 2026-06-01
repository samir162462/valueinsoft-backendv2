package com.example.valueinsoftbackend.ai.dto;

public record AiKnowledgeIngestResponse(
        AiKnowledgeDocumentDto document,
        AiKnowledgeIngestionJobDto ingestionJob
) {
}
