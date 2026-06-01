package com.example.valueinsoftbackend.ai.embedding;

import com.example.valueinsoftbackend.ai.config.AiProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GoogleAiEmbeddingProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void disabledConfigUsesDisabledProvider() {
        new ApplicationContextRunner()
                .withUserConfiguration(EmbeddingProviderTestConfig.class)
                .withPropertyValues("vls.ai.embedding.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(AiEmbeddingProvider.class);
                    assertThat(context.getBean(AiEmbeddingProvider.class)).isInstanceOf(DisabledAiEmbeddingProvider.class);
                });
    }

    @Test
    void enabledGoogleProviderRequiresApiKey() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        GoogleAiEmbeddingProvider provider = new GoogleAiEmbeddingProvider(properties(""), objectMapper, restTemplate);

        AiEmbeddingException exception = assertThrows(
                AiEmbeddingException.class,
                () -> provider.embedOne("hello")
        );

        assertEquals(AiEmbeddingException.Category.MISSING_API_KEY, exception.getCategory());
        assertEquals("Google embedding API key is not configured.", exception.getMessage());
        verify(restTemplate, never()).exchange(any(String.class), any(HttpMethod.class), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void buildsValidRequestAndParsesValidResponse() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(successResponse(2)));
        GoogleAiEmbeddingProvider provider = new GoogleAiEmbeddingProvider(properties("test-key"), objectMapper, restTemplate);

        List<AiEmbeddingResult> results = provider.embed(List.of("first text", "second text"));

        assertEquals(2, results.size());
        assertEquals(0, results.get(0).index());
        assertEquals("first text", results.get(0).text());
        assertEquals(1, results.get(1).index());
        assertEquals("second text", results.get(1).text());
        assertEquals(768, results.get(0).vector().length);
        assertEquals("google", results.get(0).provider());
        assertEquals("gemini-embedding-2", results.get(0).model());

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(urlCaptor.capture(), eq(HttpMethod.POST), entityCaptor.capture(), eq(String.class));

        assertEquals(
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-2:batchEmbedContents?key=test-key",
                urlCaptor.getValue()
        );
        assertEquals("application/json", entityCaptor.getValue().getHeaders().getFirst(HttpHeaders.CONTENT_TYPE));

        Map<String, Object> body = (Map<String, Object>) entityCaptor.getValue().getBody();
        List<Map<String, Object>> requests = (List<Map<String, Object>>) body.get("requests");
        assertEquals(2, requests.size());
        assertEquals("models/gemini-embedding-2", requests.get(0).get("model"));
        assertEquals(768, requests.get(0).get("outputDimensionality"));

        Map<String, Object> content = (Map<String, Object>) requests.get(0).get("content");
        List<Map<String, String>> parts = (List<Map<String, String>>) content.get("parts");
        assertEquals("first text", parts.get(0).get("text"));
    }

    @Test
    void rejectsWrongDimensionResponse() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("""
                        {"embeddings":[{"values":[0.1,0.2,0.3]}]}
                        """));
        GoogleAiEmbeddingProvider provider = new GoogleAiEmbeddingProvider(properties("test-key"), objectMapper, restTemplate);

        AiEmbeddingException exception = assertThrows(
                AiEmbeddingException.class,
                () -> provider.embedOne("hello")
        );

        assertEquals(AiEmbeddingException.Category.PROVIDER_BAD_RESPONSE, exception.getCategory());
        assertEquals("Google embedding vector dimension mismatch. Expected 768 but got 3.", exception.getMessage());
    }

    @Test
    void mapsUnauthorizedToAuthError() {
        assertCategoryForHttpError(HttpStatus.UNAUTHORIZED, AiEmbeddingException.Category.PROVIDER_AUTH_ERROR);
    }

    @Test
    void mapsForbiddenToAuthError() {
        assertCategoryForHttpError(HttpStatus.FORBIDDEN, AiEmbeddingException.Category.PROVIDER_AUTH_ERROR);
    }

    @Test
    void mapsRateLimitToRateLimitError() {
        assertCategoryForHttpError(HttpStatus.TOO_MANY_REQUESTS, AiEmbeddingException.Category.PROVIDER_RATE_LIMIT);
    }

    @Test
    void mapsServerErrorToProviderServerError() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(HttpServerErrorException.create(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Server Error",
                        HttpHeaders.EMPTY,
                        new byte[0],
                        StandardCharsets.UTF_8));
        GoogleAiEmbeddingProvider provider = new GoogleAiEmbeddingProvider(properties("test-key"), objectMapper, restTemplate);

        AiEmbeddingException exception = assertThrows(AiEmbeddingException.class, () -> provider.embedOne("hello"));

        assertEquals(AiEmbeddingException.Category.PROVIDER_SERVER_ERROR, exception.getCategory());
        assertEquals("Google embedding service is temporarily unavailable.", exception.getMessage());
    }

    @Test
    void embedListPreservesOrderAcrossBatches() {
        AiProperties properties = properties("test-key");
        properties.getEmbedding().getGoogle().setBatchSize(1);
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(successResponse(1)))
                .thenReturn(ResponseEntity.ok(successResponse(1)));
        GoogleAiEmbeddingProvider provider = new GoogleAiEmbeddingProvider(properties, objectMapper, restTemplate);

        List<AiEmbeddingResult> results = provider.embed(List.of("first", "second"));

        assertEquals(0, results.get(0).index());
        assertEquals("first", results.get(0).text());
        assertEquals(1, results.get(1).index());
        assertEquals("second", results.get(1).text());
    }

    @Test
    void blankTextIsRejected() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        GoogleAiEmbeddingProvider provider = new GoogleAiEmbeddingProvider(properties("test-key"), objectMapper, restTemplate);

        AiEmbeddingException exception = assertThrows(
                AiEmbeddingException.class,
                () -> provider.embed(List.of("valid", " "))
        );

        assertEquals(AiEmbeddingException.Category.VALIDATION_ERROR, exception.getCategory());
        assertEquals("Embedding text must not be blank.", exception.getMessage());
        verify(restTemplate, never()).exchange(any(String.class), any(HttpMethod.class), any(HttpEntity.class), eq(String.class));
    }

    private void assertCategoryForHttpError(HttpStatus status, AiEmbeddingException.Category expectedCategory) {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(HttpClientErrorException.create(
                        status,
                        status.getReasonPhrase(),
                        HttpHeaders.EMPTY,
                        new byte[0],
                        StandardCharsets.UTF_8));
        GoogleAiEmbeddingProvider provider = new GoogleAiEmbeddingProvider(properties("test-key"), objectMapper, restTemplate);

        AiEmbeddingException exception = assertThrows(AiEmbeddingException.class, () -> provider.embedOne("hello"));

        assertEquals(expectedCategory, exception.getCategory());
    }

    private AiProperties properties(String apiKey) {
        AiProperties properties = new AiProperties();
        properties.getEmbedding().setEnabled(true);
        properties.getEmbedding().setProvider("google");
        properties.getEmbedding().setModel("gemini-embedding-2");
        properties.getEmbedding().setDimension(768);
        properties.getEmbedding().getGoogle().setApiKey(apiKey);
        properties.getEmbedding().getGoogle().setBaseUrl("https://generativelanguage.googleapis.com/v1beta");
        properties.getEmbedding().getGoogle().setTimeoutMs(60_000);
        properties.getEmbedding().getGoogle().setBatchSize(100);
        return properties;
    }

    private String successResponse(int count) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\"embeddings\":[");
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append("{\"values\":").append(vectorJson(768, i + 1)).append('}');
        }
        builder.append("]}");
        return builder.toString();
    }

    private String vectorJson(int dimension, int seed) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < dimension; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(seed).append('.').append(i % 10);
        }
        builder.append(']');
        return builder.toString();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AiProperties.class)
    @Import({DisabledAiEmbeddingProvider.class, GoogleAiEmbeddingProvider.class})
    static class EmbeddingProviderTestConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
