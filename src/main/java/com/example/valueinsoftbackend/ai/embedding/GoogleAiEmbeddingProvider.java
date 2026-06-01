package com.example.valueinsoftbackend.ai.embedding;

import com.example.valueinsoftbackend.ai.config.AiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.SocketTimeoutException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@ConditionalOnExpression("'${vls.ai.embedding.enabled:false}' == 'true' && '${vls.ai.embedding.provider:google}'.equalsIgnoreCase('google')")
public class GoogleAiEmbeddingProvider implements AiEmbeddingProvider {

    private static final String PROVIDER_NAME = "google";
    private static final String DEFAULT_MODEL = "gemini-embedding-2";
    private static final int DEFAULT_DIMENSION = 768;
    private static final int DEFAULT_BATCH_SIZE = 100;

    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Autowired
    public GoogleAiEmbeddingProvider(AiProperties aiProperties, ObjectMapper objectMapper) {
        this(aiProperties, objectMapper, createRestTemplate(aiProperties));
    }

    GoogleAiEmbeddingProvider(AiProperties aiProperties, ObjectMapper objectMapper, RestTemplate restTemplate) {
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
    }

    @Override
    public String providerName() {
        return PROVIDER_NAME;
    }

    @Override
    public String modelName() {
        String model = embeddingProperties().getModel();
        return model == null || model.isBlank() ? DEFAULT_MODEL : model.trim();
    }

    @Override
    public int dimension() {
        int dimension = embeddingProperties().getDimension();
        return dimension > 0 ? dimension : DEFAULT_DIMENSION;
    }

    @Override
    public List<AiEmbeddingResult> embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            throw embeddingException(AiEmbeddingException.Category.VALIDATION_ERROR,
                    "At least one text value is required for embedding.",
                    null);
        }
        List<String> normalizedTexts = normalizeTexts(texts);
        AiProperties.GoogleEmbeddingProperties google = googleProperties();
        String apiKey = google.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw embeddingException(AiEmbeddingException.Category.MISSING_API_KEY,
                    "Google embedding API key is not configured.",
                    null);
        }

        List<AiEmbeddingResult> results = new ArrayList<>(normalizedTexts.size());
        int batchSize = batchSize();
        for (int offset = 0; offset < normalizedTexts.size(); offset += batchSize) {
            int end = Math.min(offset + batchSize, normalizedTexts.size());
            results.addAll(embedBatch(normalizedTexts.subList(offset, end), offset, apiKey.trim()));
        }
        if (results.size() != normalizedTexts.size()) {
            throw embeddingException(AiEmbeddingException.Category.PROVIDER_BAD_RESPONSE,
                    "Google embedding returned an unexpected number of vectors.",
                    null);
        }
        return results;
    }

    private List<AiEmbeddingResult> embedBatch(List<String> texts, int offset, String apiKey) {
        long startedAt = System.nanoTime();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<String> response = restTemplate.exchange(
                    endpointUrl(apiKey),
                    HttpMethod.POST,
                    new HttpEntity<>(requestBody(texts), headers),
                    String.class);

            List<float[]> vectors = parseVectors(response.getBody(), texts.size());
            List<AiEmbeddingResult> results = new ArrayList<>(vectors.size());
            for (int index = 0; index < vectors.size(); index++) {
                float[] vector = vectors.get(index);
                validateVectorDimension(vector);
                results.add(new AiEmbeddingResult(
                        offset + index,
                        texts.get(index),
                        vector,
                        providerName(),
                        modelName(),
                        dimension()
                ));
            }
            log.debug("Embedding provider call completed provider={} model={} batchSize={} durationMs={}",
                    providerName(),
                    modelName(),
                    texts.size(),
                    durationMs(startedAt));
            return results;
        } catch (HttpStatusCodeException exception) {
            throw mapHttpStatus(exception);
        } catch (ResourceAccessException exception) {
            AiEmbeddingException.Category category = timeoutCause(exception)
                    ? AiEmbeddingException.Category.PROVIDER_TIMEOUT
                    : AiEmbeddingException.Category.PROVIDER_UNAVAILABLE;
            throw embeddingException(category,
                    timeoutCause(exception)
                            ? "Google embedding response timed out."
                            : "Google embedding service is temporarily unavailable.",
                    exception);
        } catch (AiEmbeddingException exception) {
            throw exception;
        } catch (Exception exception) {
            throw embeddingException(AiEmbeddingException.Category.UNKNOWN_ERROR,
                    "Google embedding request failed.",
                    exception);
        }
    }

    private Map<String, Object> requestBody(List<String> texts) {
        String requestModel = requestModelName();
        int dim = dimension();
        boolean supportsDim = modelName().toLowerCase(java.util.Locale.ROOT).contains("gemini-embedding");
        List<Map<String, Object>> requests = texts.stream()
                .map(text -> {
                    Map<String, Object> req = new java.util.HashMap<>();
                    req.put("model", requestModel);
                    req.put("content", Map.of(
                            "parts", List.of(Map.of("text", text))
                    ));
                    if (supportsDim && dim > 0) {
                        req.put("outputDimensionality", dim);
                    }
                    return req;
                })
                .toList();
        return Map.of("requests", requests);
    }

    private List<float[]> parseVectors(String body, int expectedCount) {
        if (body == null || body.isBlank()) {
            throw embeddingException(AiEmbeddingException.Category.PROVIDER_BAD_RESPONSE,
                    "Google embedding returned an empty response.",
                    null);
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode embeddings = root.path("embeddings");
            if (!embeddings.isArray() || embeddings.size() != expectedCount) {
                throw embeddingException(AiEmbeddingException.Category.PROVIDER_BAD_RESPONSE,
                        "Google embedding returned an unexpected number of vectors.",
                        null);
            }

            List<float[]> vectors = new ArrayList<>(expectedCount);
            for (JsonNode embedding : embeddings) {
                JsonNode values = embedding.path("values");
                if (!values.isArray()) {
                    throw embeddingException(AiEmbeddingException.Category.PROVIDER_BAD_RESPONSE,
                            "Google embedding returned a vector with invalid values.",
                            null);
                }
                float[] vector = new float[values.size()];
                for (int i = 0; i < values.size(); i++) {
                    JsonNode value = values.get(i);
                    if (!value.isNumber()) {
                        throw embeddingException(AiEmbeddingException.Category.PROVIDER_BAD_RESPONSE,
                                "Google embedding returned a non-numeric vector value.",
                                null);
                    }
                    vector[i] = (float) value.asDouble();
                }
                vectors.add(vector);
            }
            return vectors;
        } catch (AiEmbeddingException exception) {
            throw exception;
        } catch (Exception exception) {
            throw embeddingException(AiEmbeddingException.Category.PROVIDER_BAD_RESPONSE,
                    "Google embedding returned an unreadable response.",
                    exception);
        }
    }

    private AiEmbeddingException mapHttpStatus(HttpStatusCodeException exception) {
        HttpStatusCode statusCode = exception.getStatusCode();
        AiEmbeddingException.Category category;
        String message;
        if (statusCode.value() == 401 || statusCode.value() == 403) {
            category = AiEmbeddingException.Category.PROVIDER_AUTH_ERROR;
            message = "Google embedding authentication failed.";
        } else if (statusCode.value() == 429) {
            category = AiEmbeddingException.Category.PROVIDER_RATE_LIMIT;
            message = "Google embedding rate limit exceeded.";
        } else if (statusCode.is5xxServerError()) {
            category = AiEmbeddingException.Category.PROVIDER_SERVER_ERROR;
            message = "Google embedding service is temporarily unavailable.";
        } else {
            category = AiEmbeddingException.Category.UNKNOWN_ERROR;
            message = "Google embedding request failed.";
        }
        log.warn("Embedding provider call failed provider={} model={} status={} category={}",
                providerName(),
                modelName(),
                statusCode.value(),
                category);
        return embeddingException(category, message, exception);
    }

    private void validateVectorDimension(float[] vector) {
        if (vector == null || vector.length != dimension()) {
            throw embeddingException(AiEmbeddingException.Category.PROVIDER_BAD_RESPONSE,
                    "Google embedding vector dimension mismatch. Expected "
                            + dimension()
                            + " but got "
                            + (vector == null ? 0 : vector.length)
                            + ".",
                    null);
        }
    }

    private List<String> normalizeTexts(List<String> texts) {
        List<String> normalized = new ArrayList<>(texts.size());
        for (String text : texts) {
            if (text == null || text.isBlank()) {
                throw embeddingException(AiEmbeddingException.Category.VALIDATION_ERROR,
                        "Embedding text must not be blank.",
                        null);
            }
            normalized.add(text.trim());
        }
        return normalized;
    }

    private String endpointUrl(String apiKey) {
        String baseUrl = googleProperties().getBaseUrl();
        String normalized = baseUrl == null || baseUrl.isBlank()
                ? "https://generativelanguage.googleapis.com/v1beta"
                : baseUrl.trim();
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized
                + "/models/"
                + urlEncode(urlModelName())
                + ":batchEmbedContents?key="
                + urlEncode(apiKey);
    }

    private String requestModelName() {
        String model = modelName();
        return model.startsWith("models/") ? model : "models/" + model;
    }

    private String urlModelName() {
        String model = modelName();
        return model.startsWith("models/") ? model.substring("models/".length()) : model;
    }

    private int batchSize() {
        int configured = googleProperties().getBatchSize();
        if (configured <= 0) {
            return DEFAULT_BATCH_SIZE;
        }
        return Math.min(configured, DEFAULT_BATCH_SIZE);
    }

    private AiProperties.EmbeddingProperties embeddingProperties() {
        AiProperties.EmbeddingProperties embedding = aiProperties.getEmbedding();
        return embedding == null ? new AiProperties.EmbeddingProperties() : embedding;
    }

    private AiProperties.GoogleEmbeddingProperties googleProperties() {
        AiProperties.EmbeddingProperties embedding = embeddingProperties();
        AiProperties.GoogleEmbeddingProperties google = embedding.getGoogle();
        return google == null ? new AiProperties.GoogleEmbeddingProperties() : google;
    }

    private AiEmbeddingException embeddingException(
            AiEmbeddingException.Category category,
            String safeMessage,
            Throwable cause
    ) {
        return new AiEmbeddingException(category, safeMessage, cause);
    }

    private static RestTemplate createRestTemplate(AiProperties aiProperties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        int timeout = 60_000;
        if (aiProperties.getEmbedding() != null && aiProperties.getEmbedding().getGoogle() != null) {
            timeout = Math.max(1, aiProperties.getEmbedding().getGoogle().getTimeoutMs());
        }
        requestFactory.setConnectTimeout(Duration.ofMillis(timeout));
        requestFactory.setReadTimeout(Duration.ofMillis(timeout));
        return new RestTemplate(requestFactory);
    }

    private static boolean timeoutCause(ResourceAccessException exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        String message = exception.getMessage();
        return message != null && message.toLowerCase().contains("timed out");
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static long durationMs(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000L);
    }
}
