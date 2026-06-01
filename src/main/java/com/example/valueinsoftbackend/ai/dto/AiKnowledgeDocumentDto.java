package com.example.valueinsoftbackend.ai.dto;

import java.time.Instant;
import java.util.UUID;

public record AiKnowledgeDocumentDto(
        UUID id,
        Long companyId,
        Long branchId,
        String module,
        String language,
        String documentType,
        String title,
        String sourceType,
        String sourceUri,
        String status,
        String contentPreview,
        int chunkCount,
        AiKnowledgeIngestionJobDto latestIngestionJob,
        Instant createdAt,
        Instant updatedAt
) {
}
