package com.example.valueinsoftbackend.ai.knowledge;

public class AiKnowledgeIngestionException extends RuntimeException {

    public AiKnowledgeIngestionException(String message) {
        super(message);
    }

    public AiKnowledgeIngestionException(String message, Throwable cause) {
        super(message, cause);
    }
}
