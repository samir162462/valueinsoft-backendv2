package com.example.valueinsoftbackend.ai.dto;

import java.util.List;

public record AiKnowledgeDocumentListResponse(
        List<AiKnowledgeDocumentDto> items,
        int page,
        int size,
        long total
) {
}
