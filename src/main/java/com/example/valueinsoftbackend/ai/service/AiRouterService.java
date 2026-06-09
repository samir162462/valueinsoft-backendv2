package com.example.valueinsoftbackend.ai.service;

import com.example.valueinsoftbackend.ai.config.AiProperties;
import com.example.valueinsoftbackend.ai.provider.AiProvider;
import com.example.valueinsoftbackend.ai.provider.AiProviderException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@Primary
@Slf4j
public class AiRouterService implements AiModelClient {

    private static final String DEFAULT_FAILURE_MESSAGE = "AI response is temporarily unavailable. Try again shortly.";

    private final Map<String, AiProvider> providers;
    private final AiProperties aiProperties;
    private final ObjectProvider<SpringAiChatModelClient> geminiStreamingClientProvider;

    public AiRouterService(List<AiProvider> providers,
                           AiProperties aiProperties,
                           ObjectProvider<SpringAiChatModelClient> geminiStreamingClientProvider) {
        this.providers = providers.stream()
                .collect(Collectors.toMap(provider -> normalizeProvider(provider.getName()), Function.identity(), (left, right) -> left));
        this.aiProperties = aiProperties;
        this.geminiStreamingClientProvider = geminiStreamingClientProvider;
        log.info("Initialized AiRouterService providers={} defaultProvider={} fallbackProvider={} fallbackEnabled={}",
                this.providers.keySet(),
                normalizeProvider(aiProperties.getProvider()),
                normalizeProvider(aiProperties.getFallbackProvider()),
                aiProperties.isFallbackEnabled());
    }

    @Override
    public AiModelResponse generate(AiModelRequest request) {
        String selectedProvider = selectedProvider(request);
        AiProvider provider = providers.get(selectedProvider);
        if (provider == null) {
            AiProviderException exception = new AiProviderException(
                    AiProviderException.Category.UNSUPPORTED_PROVIDER,
                    selectedProvider,
                    "Selected AI provider is not supported.");
            logProviderFailure(selectedProvider, selectedProvider, request, exception, 0, false);
            return safeFailureResponse(exception, selectedProvider);
        }

        long startedAt = System.nanoTime();
        try {
            log.debug("AI route selected requestId={} selectedProvider={} mode={}",
                    requestId(),
                    selectedProvider,
                    request == null ? null : request.mode());
            AiModelResponse response = provider.generate(request).withProvider(provider.getName());
            logProviderSuccess(selectedProvider, provider.getName(), response.modelName(), startedAt, false);
            return response;
        } catch (AiProviderException exception) {
            logProviderFailure(selectedProvider, provider.getName(), request, exception, startedAt, false);
            if (!shouldFallback(exception, selectedProvider)) {
                return safeFailureResponse(exception, selectedProvider);
            }
            return tryFallback(request, selectedProvider, exception);
        }
    }

    @Override
    public AiModelResponse generateWithFunctions(AiModelRequest request, List<ToolCallback> functions) {
        String selectedProvider = selectedProvider(request);
        if (!"gemini".equals(selectedProvider)) {
            log.debug("AI function calling skipped for provider={} because only Gemini function calling is implemented", selectedProvider);
            return generate(request);
        }

        SpringAiChatModelClient geminiClient = geminiStreamingClientProvider == null
                ? null
                : geminiStreamingClientProvider.getIfAvailable();
        if (geminiClient == null) {
            log.debug("Gemini function-calling client is unavailable; routing through non-streaming provider");
            return generate(request);
        }

        try {
            AiModelResponse response = geminiClient.generateWithFunctions(request, functions);
            if (response != null && response.fallback()) {
                log.warn("Gemini function calling returned fallback response (likely due to API key error/suspension). Attempting non-functional provider routing fallback.");
                AiModelResponse fallbackResponse = tryFallbackRouting(request);
                if (fallbackResponse != null) {
                    return fallbackResponse;
                }
            }
            return response;
        } catch (Exception exception) {
            log.warn("Gemini function calling failed with exception. Attempting non-functional provider routing fallback.", exception);
            AiModelResponse fallbackResponse = tryFallbackRouting(request);
            if (fallbackResponse != null) {
                return fallbackResponse;
            }
            throw exception;
        }
    }

    private AiModelResponse tryFallbackRouting(AiModelRequest request) {
        if (aiProperties.isFallbackEnabled()) {
            String fallbackProviderName = normalizeProvider(aiProperties.getFallbackProvider());
            if (!"gemini".equals(fallbackProviderName)) {
                log.info("Routing fallback to fallbackProvider={}", fallbackProviderName);
                return generate(new AiModelRequest(
                        request.systemPrompt(),
                        request.userMessage(),
                        request.mode(),
                        request.knowledgeContext(),
                        request.conversationContext(),
                        fallbackProviderName
                ));
            }
        }
        String defaultProviderName = normalizeProvider(aiProperties.getProvider());
        if (!"gemini".equals(defaultProviderName)) {
            log.info("Routing fallback to defaultProvider={}", defaultProviderName);
            return generate(new AiModelRequest(
                    request.systemPrompt(),
                    request.userMessage(),
                    request.mode(),
                    request.knowledgeContext(),
                    request.conversationContext(),
                    defaultProviderName
            ));
        }
        return null;
    }

    @Override
    public Flux<ChatResponse> streamWithFunctions(AiModelRequest request, List<ToolCallback> functions) {
        String selectedProvider = selectedProvider(request);
        if (!"gemini".equals(selectedProvider)) {
            log.debug("AI streaming requested with provider={} but DeepSeek streaming is deferred; using Gemini streaming compatibility path", selectedProvider);
        }

        SpringAiChatModelClient geminiClient = geminiStreamingClientProvider == null
                ? null
                : geminiStreamingClientProvider.getIfAvailable();
        if (geminiClient == null) {
            log.debug("Gemini streaming client is unavailable");
            return Flux.empty();
        }
        return geminiClient.streamWithFunctions(request, functions);
    }

    private AiModelResponse tryFallback(AiModelRequest request,
                                        String selectedProvider,
                                        AiProviderException primaryException) {
        String fallbackProviderName = normalizeProvider(aiProperties.getFallbackProvider());
        AiProvider fallbackProvider = providers.get(fallbackProviderName);
        if (fallbackProvider == null) {
            log.debug("AI fallback provider unavailable requestId={} selectedProvider={} fallbackProvider={} primaryCategory={}",
                    requestId(),
                    selectedProvider,
                    fallbackProviderName,
                    primaryException.getCategory());
            return safeFailureResponse(primaryException, selectedProvider);
        }

        long startedAt = System.nanoTime();
        try {
            log.info("AI fallback starting requestId={} selectedProvider={} fallbackProvider={} primaryCategory={}",
                    requestId(),
                    selectedProvider,
                    fallbackProviderName,
                    primaryException.getCategory());
            AiModelResponse response = fallbackProvider.generate(request).withProvider(fallbackProvider.getName());
            logProviderSuccess(selectedProvider, fallbackProvider.getName(), response.modelName(), startedAt, true);
            return response;
        } catch (AiProviderException fallbackException) {
            logProviderFailure(selectedProvider, fallbackProvider.getName(), request, fallbackException, startedAt, true);
            return safeFailureResponse(fallbackException, fallbackProviderName);
        }
    }

    private boolean shouldFallback(AiProviderException exception, String selectedProvider) {
        if (!aiProperties.isFallbackEnabled()) {
            return false;
        }
        if (exception.getCategory() == AiProviderException.Category.VALIDATION_ERROR
                || exception.getCategory() == AiProviderException.Category.UNSUPPORTED_PROVIDER) {
            return false;
        }
        String fallbackProvider = normalizeProvider(aiProperties.getFallbackProvider());
        return !fallbackProvider.isBlank() && !fallbackProvider.equals(selectedProvider);
    }

    private String selectedProvider(AiModelRequest request) {
        if (request != null && request.provider() != null && !request.provider().isBlank()) {
            return normalizeProvider(request.provider());
        }
        return normalizeProvider(aiProperties.getProvider());
    }

    private void logProviderSuccess(String selectedProvider,
                                    String actualProvider,
                                    String model,
                                    long startedAt,
                                    boolean fallbackUsed) {
        log.info("AI route completed requestId={} selectedProvider={} actualProvider={} model={} latencyMs={} fallbackUsed={}",
                requestId(),
                selectedProvider,
                normalizeProvider(actualProvider),
                model,
                latencyMs(startedAt),
                fallbackUsed);
    }

    private void logProviderFailure(String selectedProvider,
                                    String actualProvider,
                                    AiModelRequest request,
                                    AiProviderException exception,
                                    long startedAt,
                                    boolean fallbackUsed) {
        log.warn("AI route failed requestId={} selectedProvider={} actualProvider={} mode={} category={} latencyMs={} fallbackUsed={} detail={}",
                requestId(),
                selectedProvider,
                normalizeProvider(actualProvider),
                request == null ? null : request.mode(),
                exception.getCategory(),
                startedAt == 0 ? 0 : latencyMs(startedAt),
                fallbackUsed,
                safeDetail(exception));
    }

    private AiModelResponse safeFailureResponse(AiProviderException exception, String providerName) {
        String message = exception.getCategory() == AiProviderException.Category.VALIDATION_ERROR
                || exception.getCategory() == AiProviderException.Category.UNSUPPORTED_PROVIDER
                ? Optional.ofNullable(exception.getSafeMessage())
                        .filter(value -> !value.isBlank())
                        .orElse(DEFAULT_FAILURE_MESSAGE)
                : DEFAULT_FAILURE_MESSAGE;
        return new AiModelResponse(message, modelFor(providerName), true, providerName, AiModelResponse.providerCodeFor(providerName));
    }

    private String modelFor(String providerName) {
        String normalizedProvider = normalizeProvider(providerName);
        if ("deepseek".equals(normalizedProvider) && aiProperties.getDeepseek() != null) {
            return aiProperties.getDeepseek().getModel();
        }
        if ("gemini".equals(normalizedProvider) && aiProperties.getGemini() != null) {
            return aiProperties.getGemini().getModel();
        }
        return aiProperties.getModel();
    }

    private static String normalizeProvider(String provider) {
        String normalized = provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return "gemini";
        }
        if (normalized.equals("google") || normalized.equals("genai") || normalized.equals("google-genai")) {
            return "gemini";
        }
        return normalized;
    }

    private static long latencyMs(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000L);
    }

    private static String requestId() {
        String requestId = MDC.get("requestId");
        return requestId == null || requestId.isBlank() ? "n/a" : requestId;
    }

    private static String safeDetail(AiProviderException exception) {
        String message = exception.getSafeMessage();
        if (message == null || message.isBlank()) {
            return exception.getCategory().name();
        }
        String masked = message.replaceAll("(?i)(bearer\\s+)[A-Za-z0-9._\\-]+", "$1***");
        return masked.length() > 180 ? masked.substring(0, 180) + "..." : masked;
    }
}
