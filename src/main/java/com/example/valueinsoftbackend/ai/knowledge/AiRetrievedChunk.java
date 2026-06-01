package com.example.valueinsoftbackend.ai.knowledge;

import java.util.Map;
import java.util.UUID;

public record AiRetrievedChunk(
        UUID chunkId,
        UUID documentId,
        String documentTitle,
        Long companyId,
        Long branchId,
        String module,
        String language,
        String heading,
        String content,
        String contentPreview,
        double similarity,
        String sourceType,
        String sourceUri,
        Map<String, Object> metadata,
        String retrievalType
) {
}
