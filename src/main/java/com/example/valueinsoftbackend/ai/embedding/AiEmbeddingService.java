package com.example.valueinsoftbackend.ai.embedding;

import com.example.valueinsoftbackend.ai.config.AiProperties;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AiEmbeddingService {

    private final AiProperties aiProperties;
    private final Map<String, AiEmbeddingProvider> providers;

    public AiEmbeddingService(AiProperties aiProperties, List<AiEmbeddingProvider> providers) {
        this.aiProperties = aiProperties;
        this.providers = providers == null
                ? Map.of()
                : providers.stream()
                .collect(Collectors.toMap(
                        provider -> normalizeProvider(provider.providerName()),
                        Function.identity(),
                        (left, right) -> left
                ));
    }

    public AiEmbeddingResult embedQuery(String query) {
        return embedOne(query);
    }

    public AiEmbeddingResult embedOne(String text) {
        return validateResult(resolveProvider().embedOne(requireText(text)));
    }

    public List<AiEmbeddingResult> embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            throw new AiEmbeddingException("At least one text value is required for embedding.");
        }
        List<String> normalizedTexts = texts.stream()
                .map(this::requireText)
                .toList();
        List<AiEmbeddingResult> results = resolveProvider().embed(normalizedTexts);
        if (results == null || results.size() != normalizedTexts.size()) {
            throw new AiEmbeddingException("Embedding provider returned an unexpected number of results.");
        }
        return results.stream()
                .map(this::validateResult)
                .toList();
    }

    public int configuredDimension() {
        int dimension = embeddingProperties().getDimension();
        if (dimension <= 0) {
            throw new AiEmbeddingException("Embedding dimension must be configured with a positive value.");
        }
        return dimension;
    }

    public void validateDimension(float[] vector) {
        if (vector == null) {
            throw new AiEmbeddingException("Embedding vector is required.");
        }
        int configuredDimension = configuredDimension();
        if (vector.length != configuredDimension) {
            throw new AiEmbeddingException(
                    "Embedding vector dimension mismatch. Expected " + configuredDimension + " but got " + vector.length + "."
            );
        }
    }

    private AiEmbeddingResult validateResult(AiEmbeddingResult result) {
        if (result == null) {
            throw new AiEmbeddingException("Embedding provider returned a null result.");
        }
        validateDimension(result.vector());
        if (result.dimension() != configuredDimension()) {
            throw new AiEmbeddingException(
                    "Embedding result dimension metadata mismatch. Expected "
                            + configuredDimension()
                            + " but got "
                            + result.dimension()
                            + "."
            );
        }
        return result;
    }

    private AiEmbeddingProvider resolveProvider() {
        AiProperties.EmbeddingProperties embedding = embeddingProperties();
        if (!embedding.isEnabled()) {
            throw new AiEmbeddingException(
                    "AI embeddings are disabled. Set vls.ai.embedding.enabled=true and configure a real embedding provider."
            );
        }
        String providerName = normalizeProvider(embedding.getProvider());
        AiEmbeddingProvider provider = providers.get(providerName);
        if (provider == null) {
            throw new AiEmbeddingException("AI embedding provider is not configured: " + providerName);
        }
        int providerDimension = provider.dimension();
        if (providerDimension > 0 && providerDimension != configuredDimension()) {
            throw new AiEmbeddingException(
                    "Configured embedding dimension "
                            + configuredDimension()
                            + " does not match provider dimension "
                            + providerDimension
                            + "."
            );
        }
        return provider;
    }

    private AiProperties.EmbeddingProperties embeddingProperties() {
        AiProperties.EmbeddingProperties embedding = aiProperties.getEmbedding();
        return embedding == null ? new AiProperties.EmbeddingProperties() : embedding;
    }

    private String requireText(String text) {
        if (text == null || text.isBlank()) {
            throw new AiEmbeddingException("Embedding text must not be blank.");
        }
        return text.trim();
    }

    private String normalizeProvider(String provider) {
        String normalized = provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return "google";
        }
        if (normalized.equals("gemini") || normalized.equals("google-genai") || normalized.equals("genai")) {
            return "google";
        }
        return normalized;
    }
}
