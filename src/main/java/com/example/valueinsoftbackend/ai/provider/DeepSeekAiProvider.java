package com.example.valueinsoftbackend.ai.provider;

import com.example.valueinsoftbackend.ai.audit.AiUsageMeteringContext;
import com.example.valueinsoftbackend.ai.config.AiProperties;
import com.example.valueinsoftbackend.ai.service.AiModelRequest;
import com.example.valueinsoftbackend.ai.service.AiModelResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class DeepSeekAiProvider implements AiProvider {

    private static final String PROVIDER_NAME = "deepseek";
    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";

    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final AiUsageMeteringContext usageMeteringContext;

    @Autowired
    public DeepSeekAiProvider(AiProperties aiProperties, ObjectMapper objectMapper, AiUsageMeteringContext usageMeteringContext) {
        this(aiProperties, objectMapper, createRestTemplate(aiProperties), usageMeteringContext);
    }

    DeepSeekAiProvider(AiProperties aiProperties, ObjectMapper objectMapper, RestTemplate restTemplate) {
        this(aiProperties, objectMapper, restTemplate, new AiUsageMeteringContext());
    }

    DeepSeekAiProvider(AiProperties aiProperties, ObjectMapper objectMapper, RestTemplate restTemplate, AiUsageMeteringContext usageMeteringContext) {
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
        this.usageMeteringContext = usageMeteringContext;
    }

    @Override
    public String getName() {
        return PROVIDER_NAME;
    }

    @Override
    public AiModelResponse generate(AiModelRequest request) {
        validateRequest(request);
        AiProperties.DeepSeekProperties deepseek = deepseekProperties();
        String apiKey = deepseek.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw providerException(AiProviderException.Category.MISSING_API_KEY, "DeepSeek API key is not configured.", null);
        }

        long startedAt = System.nanoTime();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = Map.of(
                    "model", modelName(),
                    "messages", List.of(
                            Map.of("role", "system", "content", nullToEmpty(request.systemPrompt())),
                            Map.of("role", "user", "content", buildUserMessageContent(request))
                    ),
                    "temperature", aiProperties.getTemperature(),
                    "max_tokens", aiProperties.getMaxOutputTokens(),
                    "stream", false
            );

            log.debug("AI provider call start provider={} model={} mode={} userMessageLength={} knowledgeLength={} conversationLength={} timeoutMs={}",
                    PROVIDER_NAME,
                    modelName(),
                    request.mode(),
                    lengthOf(request.userMessage()),
                    lengthOf(request.knowledgeContext()),
                    lengthOf(request.conversationContext()),
                    timeoutMs());

            ResponseEntity<String> response = restTemplate.exchange(
                    endpointUrl(),
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    String.class);

            AiModelResponse modelResponse = parseResponse(response.getBody());
            usageMeteringContext.record(modelResponse);
            log.debug("AI provider call completed provider={} model={} durationMs={} responseLength={}",
                    PROVIDER_NAME,
                    modelName(),
                    Math.max(0, (System.nanoTime() - startedAt) / 1_000_000L),
                    lengthOf(modelResponse.answer()));
            return modelResponse;
        } catch (HttpStatusCodeException exception) {
            throw mapHttpStatus(exception);
        } catch (ResourceAccessException exception) {
            throw providerException(timeoutCause(exception)
                            ? AiProviderException.Category.PROVIDER_TIMEOUT
                            : AiProviderException.Category.UNKNOWN_ERROR,
                    timeoutCause(exception)
                            ? "DeepSeek response timed out."
                            : "DeepSeek response is temporarily unavailable.",
                    exception);
        } catch (AiProviderException exception) {
            throw exception;
        } catch (Exception exception) {
            throw providerException(AiProviderException.Category.UNKNOWN_ERROR,
                    "DeepSeek response is temporarily unavailable.",
                    exception);
        }
    }

    private AiModelResponse parseResponse(String body) {
        if (body == null || body.isBlank()) {
            throw providerException(AiProviderException.Category.PROVIDER_BAD_RESPONSE,
                    "DeepSeek returned an empty response.",
                    null);
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw providerException(AiProviderException.Category.PROVIDER_BAD_RESPONSE,
                        "DeepSeek returned no choices.",
                        null);
            }
            String content = choices.get(0).path("message").path("content").asText(null);
            if (content == null || content.isBlank()) {
                throw providerException(AiProviderException.Category.PROVIDER_BAD_RESPONSE,
                        "DeepSeek returned an empty choice.",
                        null);
            }

            int promptTokens = 0;
            int completionTokens = 0;
            int totalTokens = 0;
            int cachedPromptTokens = 0;
            JsonNode usage = root.path("usage");
            if (!usage.isMissingNode() && !usage.isNull()) {
                promptTokens = usage.path("prompt_tokens").asInt(0);
                completionTokens = usage.path("completion_tokens").asInt(0);
                totalTokens = usage.path("total_tokens").asInt(promptTokens + completionTokens);
                // DeepSeek context cache: prompt_cache_hit_tokens are billed at the
                // cached-input rate; the remainder of prompt_tokens is a cache miss.
                cachedPromptTokens = usage.path("prompt_cache_hit_tokens").asInt(0);
                log.debug("AI provider token usage provider={} model={} promptTokens={} completionTokens={} totalTokens={} cachedPromptTokens={}",
                        PROVIDER_NAME,
                        modelName(),
                        promptTokens,
                        completionTokens,
                        totalTokens,
                        cachedPromptTokens);
            }
            return new AiModelResponse(content, modelName(), false, PROVIDER_NAME, AiModelResponse.providerCodeFor(PROVIDER_NAME))
                    .withUsage(promptTokens, completionTokens, totalTokens, cachedPromptTokens);
        } catch (AiProviderException exception) {
            throw exception;
        } catch (Exception exception) {
            throw providerException(AiProviderException.Category.PROVIDER_BAD_RESPONSE,
                    "DeepSeek returned an unreadable response.",
                    exception);
        }
    }

    private AiProviderException mapHttpStatus(HttpStatusCodeException exception) {
        HttpStatusCode statusCode = exception.getStatusCode();
        if (statusCode.value() == 401 || statusCode.value() == 403) {
            return providerException(AiProviderException.Category.PROVIDER_AUTH_ERROR,
                    "DeepSeek authentication failed.",
                    exception);
        }
        if (statusCode.value() == 429) {
            return providerException(AiProviderException.Category.PROVIDER_RATE_LIMIT,
                    "DeepSeek rate limit exceeded.",
                    exception);
        }
        if (statusCode.is5xxServerError()) {
            return providerException(AiProviderException.Category.PROVIDER_SERVER_ERROR,
                    "DeepSeek service is temporarily unavailable.",
                    exception);
        }
        return providerException(AiProviderException.Category.UNKNOWN_ERROR,
                "DeepSeek request failed.",
                exception);
    }

    private void validateRequest(AiModelRequest request) {
        if (request == null || request.userMessage() == null || request.userMessage().isBlank()) {
            throw providerException(AiProviderException.Category.VALIDATION_ERROR,
                    "AI request user message is required.",
                    null);
        }
    }

    private AiProviderException providerException(AiProviderException.Category category, String safeMessage, Throwable cause) {
        return new AiProviderException(category, PROVIDER_NAME, safeMessage, cause);
    }

    private String buildUserMessageContent(AiModelRequest request) {
        boolean hasKnowledge = request.knowledgeContext() != null && !request.knowledgeContext().isBlank();
        boolean hasConversation = request.conversationContext() != null && !request.conversationContext().isBlank();
        if (!hasKnowledge && !hasConversation) {
            return request.userMessage();
        }
        StringBuilder builder = new StringBuilder();
        if (hasConversation) {
            builder.append("Recent conversation context. Use it only to resolve follow-up references; do not invent business data from it.\n")
                    .append(request.conversationContext().trim())
                    .append("\n\n");
        }
        if (hasKnowledge) {
            builder.append("Answer the user using only the internal help context below. ")
                    .append("If the context does not answer the question, say the help article is not available yet.\n\n")
                    .append("Internal help context:\n")
                    .append(request.knowledgeContext().trim())
                    .append("\n\n");
        }
        builder.append("User question:\n").append(request.userMessage());
        return builder.toString();
    }

    private String endpointUrl() {
        String baseUrl = deepseekProperties().getBaseUrl();
        String normalized = baseUrl == null || baseUrl.isBlank()
                ? "https://api.deepseek.com"
                : baseUrl.trim();
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized + CHAT_COMPLETIONS_PATH;
    }

    private String modelName() {
        String model = deepseekProperties().getModel();
        return model == null || model.isBlank() ? "deepseek-chat" : model;
    }

    private AiProperties.DeepSeekProperties deepseekProperties() {
        if (aiProperties.getDeepseek() == null) {
            aiProperties.setDeepseek(new AiProperties.DeepSeekProperties());
        }
        return aiProperties.getDeepseek();
    }

    private int timeoutMs() {
        int timeout = deepseekProperties().getTimeoutMs();
        return timeout > 0 ? timeout : 60_000;
    }

    private static RestTemplate createRestTemplate(AiProperties aiProperties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        int timeout = aiProperties.getDeepseek() == null ? 60_000 : Math.max(1, aiProperties.getDeepseek().getTimeoutMs());
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

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static int lengthOf(String value) {
        return value == null ? 0 : value.length();
    }

    private static String optionalInt(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        return value.isNumber() ? String.valueOf(value.asInt()) : "unknown";
    }
}
