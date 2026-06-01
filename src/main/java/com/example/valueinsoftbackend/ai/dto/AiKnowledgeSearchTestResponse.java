package com.example.valueinsoftbackend.ai.dto;

import java.util.List;

public record AiKnowledgeSearchTestResponse(
        String query,
        List<AiRetrievedChunkDto> items
) {
}
