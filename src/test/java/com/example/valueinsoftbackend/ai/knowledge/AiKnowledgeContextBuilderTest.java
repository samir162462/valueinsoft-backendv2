package com.example.valueinsoftbackend.ai.knowledge;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiKnowledgeContextBuilderTest {

    private final AiKnowledgeContextBuilder builder = new AiKnowledgeContextBuilder();

    @Test
    void buildsCompactContextWithSourceTitleAndModule() {
        String context = builder.buildContext(java.util.List.of(chunk("Inventory Manual", "inventory", "Add product steps.")));

        assertTrue(context.contains("[Source 1]"));
        assertTrue(context.contains("Title: Inventory Manual"));
        assertTrue(context.contains("Module: inventory"));
        assertTrue(context.contains("Similarity: 0.84"));
        assertTrue(context.contains("Add product steps."));
    }

    @Test
    void truncatesLongChunksAndDoesNotIncludeMetadataSecrets() {
        String longContent = "word ".repeat(100);
        String context = builder.buildContext(java.util.List.of(chunk("Secret Manual", "inventory", longContent)), 80);

        assertTrue(context.contains("..."));
        assertFalse(context.contains("apiKey"));
        assertFalse(context.contains("secret-value"));
    }

    private AiRetrievedChunk chunk(String title, String module, String content) {
        return new AiRetrievedChunk(
                UUID.randomUUID(),
                UUID.randomUUID(),
                title,
                1L,
                null,
                module,
                "en",
                null,
                content,
                content,
                0.84,
                "USER_MANUAL",
                null,
                Map.of("apiKey", "secret-value"),
                "VECTOR"
        );
    }
}
