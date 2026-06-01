package com.example.valueinsoftbackend.ai.dto;

import java.util.UUID;

public record AiRetrievedChunkDto(
        UUID chunkId,
        UUID documentId,
        String documentTitle,
        String module,
        Long branchId,
        String language,
        String heading,
        String contentPreview,
        double similarity,
        String retrievalType
) {
}
