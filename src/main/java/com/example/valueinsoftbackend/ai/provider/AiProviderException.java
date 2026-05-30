package com.example.valueinsoftbackend.ai.provider;

public class AiProviderException extends RuntimeException {

    private final Category category;
    private final String providerName;
    private final String safeMessage;

    public AiProviderException(Category category, String providerName, String safeMessage) {
        this(category, providerName, safeMessage, null);
    }

    public AiProviderException(Category category, String providerName, String safeMessage, Throwable cause) {
        super(safeMessage, cause);
        this.category = category;
        this.providerName = providerName;
        this.safeMessage = safeMessage;
    }

    public Category getCategory() {
        return category;
    }

    public String getProviderName() {
        return providerName;
    }

    public String getSafeMessage() {
        return safeMessage;
    }

    public enum Category {
        VALIDATION_ERROR,
        UNSUPPORTED_PROVIDER,
        MISSING_API_KEY,
        PROVIDER_AUTH_ERROR,
        PROVIDER_RATE_LIMIT,
        PROVIDER_TIMEOUT,
        PROVIDER_SERVER_ERROR,
        PROVIDER_BAD_RESPONSE,
        UNKNOWN_ERROR
    }
}
