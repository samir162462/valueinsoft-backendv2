package com.example.valueinsoftbackend.ai.dto;

import java.time.Instant;
import java.util.UUID;

public record AiKnowledgeIngestionJobDto(
        UUID jobId,
        UUID documentId,
        String status,
        int chunkCount,
        String embeddingModel,
        String errorMessage,
        Instant startedAt,
        Instant finishedAt,
        Instant createdAt
) {
}
