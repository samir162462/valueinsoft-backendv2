package com.example.valueinsoftbackend.ai.rag;

public record AiKnowledgeSearchResult(
        AiDocumentChunkRecord chunk,
        int score
) {
}
