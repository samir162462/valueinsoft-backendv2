package com.example.valueinsoftbackend.ai.embedding;

import java.util.List;

public interface AiEmbeddingProvider {

    String providerName();

    String modelName();

    int dimension();

    List<AiEmbeddingResult> embed(List<String> texts);

    default AiEmbeddingResult embedOne(String text) {
        List<AiEmbeddingResult> results = embed(List.of(text));
        if (results.isEmpty()) {
            throw new AiEmbeddingException("Embedding provider returned no result.");
        }
        return results.get(0);
    }
}
