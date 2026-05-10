package com.example.valueinsoftbackend.ai.service;

public record AiModelResponse(
        String answer,
        String modelName,
        boolean fallback
) {
}
