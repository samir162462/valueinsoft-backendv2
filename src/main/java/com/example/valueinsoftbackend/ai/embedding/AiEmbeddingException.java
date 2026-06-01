package com.example.valueinsoftbackend.ai.embedding;

public class AiEmbeddingException extends RuntimeException {

    public enum Category {
        VALIDATION_ERROR,
        MISSING_API_KEY,
        PROVIDER_AUTH_ERROR,
        PROVIDER_RATE_LIMIT,
        PROVIDER_SERVER_ERROR,
        PROVIDER_TIMEOUT,
        PROVIDER_BAD_RESPONSE,
        PROVIDER_UNAVAILABLE,
        UNKNOWN_ERROR
    }

    private final Category category;

    public AiEmbeddingException(String message) {
        this(Category.UNKNOWN_ERROR, message, null);
    }

    public AiEmbeddingException(String message, Throwable cause) {
        this(Category.UNKNOWN_ERROR, message, cause);
    }

    public AiEmbeddingException(Category category, String message) {
        this(category, message, null);
    }

    public AiEmbeddingException(Category category, String message, Throwable cause) {
        super(message, cause);
        this.category = category == null ? Category.UNKNOWN_ERROR : category;
    }

    public Category getCategory() {
        return category;
    }
}
