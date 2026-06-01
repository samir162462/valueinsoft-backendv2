package com.example.valueinsoftbackend.ai.knowledge;

import java.time.Instant;
import java.util.UUID;

public record AiKnowledgeDocumentRecord(
        UUID id,
        Long companyId,
        Long branchId,
        String module,
        String language,
        String documentType,
        String title,
        String sourceType,
        String sourceUri,
        String contentHash,
        String rawContent,
        String normalizedContent,
        String status,
        String metadataJson,
        Long createdByUserId,
        Long updatedByUserId,
        Instant createdAt,
        Instant updatedAt
) {
}
