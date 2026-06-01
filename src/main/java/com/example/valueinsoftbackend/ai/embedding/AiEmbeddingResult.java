package com.example.valueinsoftbackend.ai.embedding;

public record AiEmbeddingResult(
        int index,
        String text,
        float[] vector,
        String provider,
        String model,
        int dimension
) {
}
