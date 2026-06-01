package com.example.valueinsoftbackend.ai.dto;

public record AiKnowledgeSearchTestRequest(
        String query,
        Long companyId,
        Long branchId,
        String module,
        String language,
        Integer topK,
        Double threshold
) {
}
