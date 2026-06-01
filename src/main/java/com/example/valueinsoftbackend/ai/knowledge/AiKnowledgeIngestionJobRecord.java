package com.example.valueinsoftbackend.ai.knowledge;

import java.time.Instant;
import java.util.UUID;

public record AiKnowledgeIngestionJobRecord(
        UUID id,
        UUID documentId,
        Long companyId,
        Long branchId,
        String status,
        String embeddingModel,
        int chunkCount,
        String errorMessage,
        String metadataJson,
        Instant startedAt,
        Instant finishedAt,
        Instant createdAt
) {
}
