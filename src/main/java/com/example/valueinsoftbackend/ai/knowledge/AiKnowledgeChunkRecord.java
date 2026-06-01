package com.example.valueinsoftbackend.ai.knowledge;

import java.time.Instant;
import java.util.UUID;

public record AiKnowledgeChunkRecord(
        UUID id,
        UUID documentId,
        Long companyId,
        Long branchId,
        String module,
        String language,
        int chunkIndex,
        String heading,
        String content,
        int tokenCount,
        float[] embedding,
        String embeddingModel,
        String status,
        String metadataJson,
        Instant createdAt
) {
}
