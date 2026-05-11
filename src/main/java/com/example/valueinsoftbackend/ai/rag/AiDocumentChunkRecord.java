package com.example.valueinsoftbackend.ai.rag;

import java.time.Instant;
import java.util.UUID;

public record AiDocumentChunkRecord(
        UUID id,
        UUID documentId,
        Long companyId,
        String title,
        String module,
        String language,
        int chunkIndex,
        String content,
        String metadataJson,
        Instant createdAt
) {
}
