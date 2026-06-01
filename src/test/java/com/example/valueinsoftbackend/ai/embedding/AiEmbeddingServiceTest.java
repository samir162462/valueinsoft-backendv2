package com.example.valueinsoftbackend.ai.embedding;

import com.example.valueinsoftbackend.ai.config.AiProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiEmbeddingServiceTest {

    @Test
    void defaultsKeepRagAndEmbeddingsDisabled() {
        AiProperties properties = new AiProperties();

        assertFalse(properties.isRagEnabled());
        assertFalse(properties.getRag().isEnabled());
        assertEquals(5, properties.getRag().getTopK());
        assertEquals(0.72, properties.getRag().getSimilarityThreshold());
        assertEquals(700, properties.getRag().getChunkTargetTokens());
        assertEquals(120, properties.getRag().getChunkOverlapTokens());
        assertEquals("en", properties.getRag().getDefaultLanguage());
        assertTrue(properties.getEmbedding().isEnabled());
        assertEquals("google", properties.getEmbedding().getProvider());
        assertEquals("gemini-embedding-2", properties.getEmbedding().getModel());
        assertEquals(768, properties.getEmbedding().getDimension());
        assertEquals("", properties.getEmbedding().getGoogle().getApiKey());
        assertEquals("https://generativelanguage.googleapis.com/v1beta", properties.getEmbedding().getGoogle().getBaseUrl());
        assertEquals(60_000, properties.getEmbedding().getGoogle().getTimeoutMs());
    }

    @Test
    void disabledEmbeddingConfigFailsClearly() {
        AiProperties properties = new AiProperties();
        properties.getEmbedding().setEnabled(false);
        AiEmbeddingService service = new AiEmbeddingService(properties, List.of());

        AiEmbeddingException exception = assertThrows(
                AiEmbeddingException.class,
                () -> service.embedOne("hello")
        );

        assertEquals(
                "AI embeddings are disabled. Set vls.ai.embedding.enabled=true and configure a real embedding provider.",
                exception.getMessage()
        );
    }

    @Test
    void enabledEmbeddingWithoutProviderFailsClearly() {
        AiProperties properties = new AiProperties();
        properties.getEmbedding().setEnabled(true);
        properties.getEmbedding().setProvider("google");

        AiEmbeddingService service = new AiEmbeddingService(properties, List.of());

        AiEmbeddingException exception = assertThrows(
                AiEmbeddingException.class,
                () -> service.embedOne("hello")
        );

        assertEquals("AI embedding provider is not configured: google", exception.getMessage());
    }

    @Test
    void validatesVectorDimension() {
        AiProperties properties = new AiProperties();
        properties.getEmbedding().setEnabled(true);
        properties.getEmbedding().setDimension(3);
        AiEmbeddingService service = new AiEmbeddingService(properties, List.of(new TestEmbeddingProvider(3)));

        AiEmbeddingResult result = service.embedOne("hello");

        assertEquals(3, result.dimension());
        assertEquals(3, result.vector().length);
    }

    @Test
    void rejectsProviderDimensionMismatch() {
        AiProperties properties = new AiProperties();
        properties.getEmbedding().setEnabled(true);
        properties.getEmbedding().setDimension(3);
        AiEmbeddingService service = new AiEmbeddingService(properties, List.of(new TestEmbeddingProvider(2)));

        AiEmbeddingException exception = assertThrows(
                AiEmbeddingException.class,
                () -> service.embedOne("hello")
        );

        assertEquals(
                "Configured embedding dimension 3 does not match provider dimension 2.",
                exception.getMessage()
        );
    }

    private static class TestEmbeddingProvider implements AiEmbeddingProvider {
        private final int dimension;

        private TestEmbeddingProvider(int dimension) {
            this.dimension = dimension;
        }

        @Override
        public String providerName() {
            return "google";
        }

        @Override
        public String modelName() {
            return "test-embedding";
        }

        @Override
        public int dimension() {
            return dimension;
        }

        @Override
        public List<AiEmbeddingResult> embed(List<String> texts) {
            return texts.stream()
                    .map(text -> new AiEmbeddingResult(
                            texts.indexOf(text),
                            text,
                            new float[dimension],
                            providerName(),
                            modelName(),
                            dimension
                    ))
                    .toList();
        }
    }
}
