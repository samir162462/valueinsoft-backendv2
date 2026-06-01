package com.example.valueinsoftbackend.ai.dto;

public record AiKnowledgeDocumentCreateRequest(
        String title,
        String documentType,
        String module,
        Long companyId,
        Long branchId,
        String language,
        String sourceType,
        String sourceUri,
        String content,
        Boolean ingestNow
) {
}
