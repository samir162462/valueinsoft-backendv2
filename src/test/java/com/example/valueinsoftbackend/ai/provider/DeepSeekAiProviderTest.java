package com.example.valueinsoftbackend.ai.provider;

import com.example.valueinsoftbackend.ai.config.AiProperties;
import com.example.valueinsoftbackend.ai.service.AiModelRequest;
import com.example.valueinsoftbackend.ai.service.AiModelResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeepSeekAiProviderTest {

    private static final String SUCCESS_RESPONSE = """
            {
              "choices": [
                {
                  "message": {
                    "content": "DeepSeek answer"
                  }
                }
              ],
              "usage": {
                "prompt_tokens": 10,
                "completion_tokens": 5,
                "total_tokens": 15
              }
            }
            """;

    private final AiModelRequest request = new AiModelRequest("system prompt", "user prompt", "CHAT", "");

    @Test
    void buildsCorrectRequestBody() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(
                eq("https://api.deepseek.com/chat/completions"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)))
                .thenReturn(ResponseEntity.ok(SUCCESS_RESPONSE));
        DeepSeekAiProvider provider = new DeepSeekAiProvider(properties("test-key"), new ObjectMapper(), restTemplate);

        provider.generate(request);

        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                eq("https://api.deepseek.com/chat/completions"),
                eq(HttpMethod.POST),
                entityCaptor.capture(),
                eq(String.class));
        HttpEntity entity = entityCaptor.getValue();
        assertEquals("Bearer test-key", entity.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));

        Map<String, Object> body = (Map<String, Object>) entity.getBody();
        assertEquals("deepseek-chat", body.get("model"));
        assertEquals(false, body.get("stream"));
        assertEquals(0.2, body.get("temperature"));
        assertEquals(2000, body.get("max_tokens"));

        List<Map<String, String>> messages = (List<Map<String, String>>) body.get("messages");
        assertEquals("system", messages.get(0).get("role"));
        assertEquals("system prompt", messages.get(0).get("content"));
        assertEquals("user", messages.get(1).get("role"));
        assertEquals("user prompt", messages.get(1).get("content"));
    }

    @Test
    void parsesChoiceMessageContent() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(SUCCESS_RESPONSE));
        DeepSeekAiProvider provider = new DeepSeekAiProvider(properties("test-key"), new ObjectMapper(), restTemplate);

        AiModelResponse response = provider.generate(request);

        assertEquals("DeepSeek answer", response.answer());
        assertEquals("deepseek-chat", response.modelName());
        assertFalse(response.fallback());
    }

    @Test
    void handlesUnauthorizedAsProviderAuthError() {
        assertCategoryForHttpError(HttpStatus.UNAUTHORIZED, AiProviderException.Category.PROVIDER_AUTH_ERROR);
    }

    @Test
    void handlesRateLimitAsProviderRateLimit() {
        assertCategoryForHttpError(HttpStatus.TOO_MANY_REQUESTS, AiProviderException.Category.PROVIDER_RATE_LIMIT);
    }

    @Test
    void handlesServerErrorAsProviderServerError() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(HttpServerErrorException.create(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Server Error",
                        HttpHeaders.EMPTY,
                        new byte[0],
                        StandardCharsets.UTF_8));
        DeepSeekAiProvider provider = new DeepSeekAiProvider(properties("test-key"), new ObjectMapper(), restTemplate);

        AiProviderException exception = assertThrows(AiProviderException.class, () -> provider.generate(request));

        assertEquals(AiProviderException.Category.PROVIDER_SERVER_ERROR, exception.getCategory());
    }

    @Test
    void handlesTimeoutAsProviderTimeout() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new ResourceAccessException("Read timed out", new SocketTimeoutException("Read timed out")));
        DeepSeekAiProvider provider = new DeepSeekAiProvider(properties("test-key"), new ObjectMapper(), restTemplate);

        AiProviderException exception = assertThrows(AiProviderException.class, () -> provider.generate(request));

        assertEquals(AiProviderException.Category.PROVIDER_TIMEOUT, exception.getCategory());
    }

    @Test
    void handlesBadResponseAsProviderBadResponse() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"choices\":[]}"));
        DeepSeekAiProvider provider = new DeepSeekAiProvider(properties("test-key"), new ObjectMapper(), restTemplate);

        AiProviderException exception = assertThrows(AiProviderException.class, () -> provider.generate(request));

        assertEquals(AiProviderException.Category.PROVIDER_BAD_RESPONSE, exception.getCategory());
    }

    @Test
    void missingApiKeyReturnsMissingApiKey() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        DeepSeekAiProvider provider = new DeepSeekAiProvider(properties(""), new ObjectMapper(), restTemplate);

        AiProviderException exception = assertThrows(AiProviderException.class, () -> provider.generate(request));

        assertEquals(AiProviderException.Category.MISSING_API_KEY, exception.getCategory());
        verify(restTemplate, never()).exchange(any(String.class), any(HttpMethod.class), any(HttpEntity.class), eq(String.class));
    }

    private void assertCategoryForHttpError(HttpStatus status, AiProviderException.Category expectedCategory) {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(HttpClientErrorException.create(
                        status,
                        status.getReasonPhrase(),
                        HttpHeaders.EMPTY,
                        new byte[0],
                        StandardCharsets.UTF_8));
        DeepSeekAiProvider provider = new DeepSeekAiProvider(properties("test-key"), new ObjectMapper(), restTemplate);

        AiProviderException exception = assertThrows(AiProviderException.class, () -> provider.generate(request));

        assertEquals(expectedCategory, exception.getCategory());
    }

    private AiProperties properties(String apiKey) {
        AiProperties properties = new AiProperties();
        properties.getDeepseek().setApiKey(apiKey);
        properties.getDeepseek().setBaseUrl("https://api.deepseek.com");
        properties.getDeepseek().setModel("deepseek-chat");
        properties.getDeepseek().setTimeoutMs(60_000);
        return properties;
    }
}
