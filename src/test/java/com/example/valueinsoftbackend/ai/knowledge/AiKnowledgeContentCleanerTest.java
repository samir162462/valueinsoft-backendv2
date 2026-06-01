package com.example.valueinsoftbackend.ai.knowledge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiKnowledgeContentCleanerTest {

    private final AiKnowledgeContentCleaner cleaner = new AiKnowledgeContentCleaner();

    @Test
    void stripsHtmlScriptAndNormalizesWhitespace() {
        String cleaned = cleaner.clean("<h1>Title</h1><script>alert('x')</script><p>Hello     world</p>");

        assertTrue(cleaned.contains("Title"));
        assertTrue(cleaned.contains("Hello world"));
        assertFalse(cleaned.contains("script"));
        assertFalse(cleaned.contains("alert"));
        assertFalse(cleaned.contains("<p>"));
    }

    @Test
    void redactsBearerTokenPasswordApiKeyJwtAndConnectionString() {
        String cleaned = cleaner.clean("""
                Authorization: Bearer abcdefghijklmnopqrstuvwxyz123456
                password=my-secret-password
                api_key=sk_abcdefghijklmnopqrstuvwxyz123456
                token: eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJzYW0ifQ.signature123456789
                database=postgresql://user:pass@localhost:5432/db
                aws=AKIAABCDEFGHIJKLMNOP
                """);

        assertTrue(cleaned.contains("Bearer [REDACTED]"));
        assertTrue(cleaned.contains("password=[REDACTED]"));
        assertTrue(cleaned.contains("api_key=[REDACTED]"));
        assertTrue(cleaned.contains("[REDACTED_JWT]"));
        assertTrue(cleaned.contains("[REDACTED_CONNECTION_STRING]"));
        assertTrue(cleaned.contains("[REDACTED_AWS_ACCESS_KEY]"));
        assertFalse(cleaned.contains("my-secret-password"));
        assertFalse(cleaned.contains("postgresql://user"));
    }
}
