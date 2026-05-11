package com.example.valueinsoftbackend.ai.rag;

import java.time.Instant;
import java.util.UUID;

public record AiDocumentRecord(
        UUID id,
        Long companyId,
        String title,
        String documentType,
        String module,
        String language,
        String content,
        String metadataJson,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
