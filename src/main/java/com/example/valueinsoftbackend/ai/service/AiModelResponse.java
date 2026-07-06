package com.example.valueinsoftbackend.ai.service;

public record AiModelResponse(
        String answer,
        String modelName,
        boolean fallback,
        String providerName,
        String providerCode,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        int cachedPromptTokens
) {
    public AiModelResponse(String answer, String modelName, boolean fallback) {
        this(answer, modelName, fallback, inferProviderName(modelName), providerCodeFor(inferProviderName(modelName)), 0, 0, 0, 0);
    }

    public AiModelResponse(String answer, String modelName, boolean fallback, String providerName, String providerCode) {
        this(answer, modelName, fallback, providerName, providerCode, 0, 0, 0, 0);
    }

    public AiModelResponse withProvider(String providerName) {
        return new AiModelResponse(answer, modelName, fallback, providerName, providerCodeFor(providerName),
                promptTokens, completionTokens, totalTokens, cachedPromptTokens);
    }

    public AiModelResponse withUsage(int promptTokens, int completionTokens, int totalTokens) {
        return withUsage(promptTokens, completionTokens, totalTokens, 0);
    }

    public AiModelResponse withUsage(int promptTokens, int completionTokens, int totalTokens, int cachedPromptTokens) {
        return new AiModelResponse(answer, modelName, fallback, providerName, providerCode,
                Math.max(0, promptTokens), Math.max(0, completionTokens), Math.max(0, totalTokens),
                Math.max(0, cachedPromptTokens));
    }

    public static String providerCodeFor(String providerName) {
        String normalized = providerName == null ? "" : providerName.trim().toLowerCase();
        if (normalized.equals("deepseek") || normalized.equals("ds")) {
            return "DS";
        }
        if (normalized.equals("gemini")
                || normalized.equals("google")
                || normalized.equals("google-genai")
                || normalized.equals("genai")
                || normalized.equals("gem")) {
            return "GEM";
        }
        return "";
    }

    private static String inferProviderName(String modelName) {
        String normalized = modelName == null ? "" : modelName.trim().toLowerCase();
        if (normalized.contains("deepseek")) {
            return "deepseek";
        }
        if (normalized.contains("gemini")) {
            return "gemini";
        }
        return "";
    }
}
