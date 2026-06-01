package com.example.valueinsoftbackend.ai.knowledge;

import com.example.valueinsoftbackend.ai.config.AiProperties;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiKnowledgeChunkingServiceTest {

    @Test
    void splitsLongTextAndAppliesOverlap() {
        AiKnowledgeChunkingService service = service(10, 2);
        AiKnowledgeDocumentRecord document = document();
        String content = IntStream.rangeClosed(1, 25)
                .mapToObj(index -> "word" + index)
                .reduce((left, right) -> left + " " + right)
                .orElseThrow();

        List<AiKnowledgeChunkRecord> chunks = service.chunk(document, content);

        assertEquals(3, chunks.size());
        assertTrue(chunks.get(0).content().startsWith("word1 "));
        assertTrue(chunks.get(1).content().startsWith("word9 "));
        assertTrue(chunks.get(2).content().startsWith("word17 "));
        assertEquals(0, chunks.get(0).chunkIndex());
        assertEquals(1, chunks.get(1).chunkIndex());
    }

    @Test
    void preservesMarkdownHeading() {
        AiKnowledgeChunkingService service = service(20, 2);
        List<AiKnowledgeChunkRecord> chunks = service.chunk(document(), """
                # Inventory Setup
                Add products and configure stock rules.
                """);

        assertEquals(1, chunks.size());
        assertEquals("Inventory Setup", chunks.get(0).heading());
        assertTrue(chunks.get(0).content().contains("Add products"));
    }

    @Test
    void handlesEmptyText() {
        AiKnowledgeChunkingService service = service(20, 2);

        assertTrue(service.chunk(document(), "   ").isEmpty());
    }

    private AiKnowledgeChunkingService service(int target, int overlap) {
        AiProperties properties = new AiProperties();
        properties.getRag().setChunkTargetTokens(target);
        properties.getRag().setChunkOverlapTokens(overlap);
        return new AiKnowledgeChunkingService(properties);
    }

    private AiKnowledgeDocumentRecord document() {
        return new AiKnowledgeDocumentRecord(
                UUID.randomUUID(),
                1L,
                null,
                "inventory",
                "en",
                "HELP_ARTICLE",
                "Inventory Help",
                "MANUAL",
                null,
                null,
                null,
                "content",
                "DRAFT",
                "{}",
                10L,
                10L,
                Instant.now(),
                Instant.now()
        );
    }
}
