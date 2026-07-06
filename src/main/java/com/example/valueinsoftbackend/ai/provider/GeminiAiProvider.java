package com.example.valueinsoftbackend.ai.provider;

import com.example.valueinsoftbackend.ai.audit.AiUsageMeteringContext;
import com.example.valueinsoftbackend.ai.config.AiProperties;
import com.example.valueinsoftbackend.ai.service.AiModelRequest;
import com.example.valueinsoftbackend.ai.service.AiModelResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@ConditionalOnClass(ChatModel.class)
@Slf4j
public class GeminiAiProvider implements AiProvider {

    private static final String PROVIDER_NAME = "gemini";

    private final ChatModel chatModel;
    private final AiProperties aiProperties;
    private final AiUsageMeteringContext usageMeteringContext;

    public GeminiAiProvider(Map<String, ChatModel> chatModels, AiProperties aiProperties, AiUsageMeteringContext usageMeteringContext) {
        this.aiProperties = aiProperties;
        this.usageMeteringContext = usageMeteringContext;
        this.chatModel = chatModels.entrySet().stream()
                .filter(entry -> entry.getKey().toLowerCase().contains("google"))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseGet(() -> chatModels.values().stream().findFirst().orElse(null));

        if (this.chatModel != null) {
            log.info("Initializing GeminiAiProvider with ChatModel bean={} model={}",
                    this.chatModel.getClass().getSimpleName(),
                    modelName());
        } else {
            log.error("No ChatModel bean found for Gemini. Gemini AI calls will fail.");
        }
    }

    @Override
    public String getName() {
        return PROVIDER_NAME;
    }

    @Override
    public AiModelResponse generate(AiModelRequest request) {
        if (chatModel == null) {
            throw new AiProviderException(
                    AiProviderException.Category.PROVIDER_BAD_RESPONSE,
                    PROVIDER_NAME,
                    "Gemini AI engine is not loaded.");
        }

        try {
            log.debug("AI provider call queued provider={} model={} mode={} systemPromptLength={} userMessageLength={} knowledgeLength={} conversationLength={} timeoutMs={}",
                    PROVIDER_NAME,
                    modelName(),
                    request.mode(),
                    lengthOf(request.systemPrompt()),
                    lengthOf(request.userMessage()),
                    lengthOf(request.knowledgeContext()),
                    lengthOf(request.conversationContext()),
                    timeoutMs());
            AiModelResponse modelResponse = CompletableFuture.supplyAsync(() -> callModel(request))
                    .orTimeout(Math.max(1, timeoutMs()), TimeUnit.MILLISECONDS)
                    .join();
            usageMeteringContext.record(modelResponse);
            return modelResponse;
        } catch (RuntimeException exception) {
            Throwable cause = unwrap(exception);
            if (cause instanceof TimeoutException) {
                throw new AiProviderException(
                        AiProviderException.Category.PROVIDER_TIMEOUT,
                        PROVIDER_NAME,
                        "Gemini response timed out.",
                        cause);
            }
            throw new AiProviderException(
                    AiProviderException.Category.UNKNOWN_ERROR,
                    PROVIDER_NAME,
                    "Gemini response is temporarily unavailable.",
                    cause);
        }
    }

    public AiModelResponse generateWithFunctions(AiModelRequest request, List<ToolCallback> functions) {
        if (chatModel == null) {
            log.debug("Gemini model skipped because no ChatModel bean is loaded mode={} configuredModel={}", request.mode(), modelName());
            return fallbackResponse("AI engine not loaded correctly.");
        }
        if (functions == null || functions.isEmpty() || !aiProperties.isFunctionCallingEnabled()) {
            try {
                return generate(request);
            } catch (AiProviderException exception) {
                log.error("Gemini model call without functions failed category={} detail={}",
                        exception.getCategory(),
                        safeDetail(exception));
                return fallbackResponse("AI response timed out or is temporarily unavailable. Try again shortly.");
            }
        }

        try {
            log.debug("Gemini model call with functions queued model={} functionsCount={}",
                    modelName(),
                    functions.size());
            AiModelResponse modelResponse = CompletableFuture.supplyAsync(() -> callModelWithFunctions(request, functions))
                    .orTimeout(Math.max(1, timeoutMs()), TimeUnit.MILLISECONDS)
                    .exceptionally(exception -> {
                        log.error("Gemini model call with functions failed category={} detail={}",
                                exceptionCategory(unwrap(exception)),
                                safeDetail(unwrap(exception)));
                        return fallbackResponse("AI response timed out or is temporarily unavailable. Try again shortly.");
                    })
                    .join();
            usageMeteringContext.record(modelResponse);
            return modelResponse;
        } catch (RuntimeException exception) {
            log.error("Gemini model call with functions runtime exception category={} detail={}",
                    exceptionCategory(unwrap(exception)),
                    safeDetail(unwrap(exception)));
            return fallbackResponse("AI response timed out or is temporarily unavailable. Try again shortly.");
        }
    }

    public Flux<ChatResponse> streamWithFunctions(AiModelRequest request, List<ToolCallback> functions) {
        if (chatModel == null) {
            log.debug("Gemini model skipped streaming because no ChatModel bean is loaded");
            return Flux.empty();
        }

        String userMessageContent = buildUserMessageContent(request);
        log.debug("Gemini model stream with functions start bean={} mode={} functionsCount={}",
                chatModel.getClass().getSimpleName(),
                request.mode(),
                functions != null ? functions.size() : 0);

        SystemMessage systemMessage = new SystemMessage(request.systemPrompt());
        UserMessage userMessage = new UserMessage(userMessageContent);

        GoogleGenAiChatOptions.Builder optionsBuilder = GoogleGenAiChatOptions.builder();
        if (functions != null && !functions.isEmpty() && aiProperties.isFunctionCallingEnabled()) {
            optionsBuilder.toolCallbacks(functions);
        }

        Prompt prompt = new Prompt(List.of(systemMessage, userMessage), optionsBuilder.build());
        return chatModel.stream(prompt);
    }

    String buildUserMessageContent(AiModelRequest request) {
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

    private AiModelResponse callModel(AiModelRequest request) {
        String userMessageContent = buildUserMessageContent(request);
        long startedAt = System.nanoTime();
        log.debug("Gemini model call start bean={} mode={} finalUserMessageLength={}",
                chatModel.getClass().getSimpleName(),
                request.mode(),
                userMessageContent.length());

        SystemMessage systemMessage = new SystemMessage(request.systemPrompt());
        UserMessage userMessage = new UserMessage(userMessageContent);

        ChatResponse chatResponse = chatModel.call(new Prompt(List.of(systemMessage, userMessage)));
        String content = chatResponse
                .getResult()
                .getOutput()
                .getText();
        log.debug("Gemini model call completed bean={} mode={} durationMs={} responseLength={}",
                chatModel.getClass().getSimpleName(),
                request.mode(),
                Math.max(0, (System.nanoTime() - startedAt) / 1_000_000L),
                lengthOf(content));
        return withUsageFrom(chatResponse,
                new AiModelResponse(content, modelName(), false, PROVIDER_NAME, AiModelResponse.providerCodeFor(PROVIDER_NAME)));
    }

    private AiModelResponse callModelWithFunctions(AiModelRequest request, List<ToolCallback> functions) {
        String userMessageContent = buildUserMessageContent(request);
        long startedAt = System.nanoTime();
        log.debug("Gemini model call with functions start bean={} mode={} functionsCount={}",
                chatModel.getClass().getSimpleName(),
                request.mode(),
                functions.size());

        SystemMessage systemMessage = new SystemMessage(request.systemPrompt());
        UserMessage userMessage = new UserMessage(userMessageContent);

        ChatOptions options = GoogleGenAiChatOptions.builder()
                .toolCallbacks(functions)
                .build();

        ChatResponse chatResponse = chatModel.call(new Prompt(List.of(systemMessage, userMessage), options));
        String content = chatResponse
                .getResult()
                .getOutput()
                .getText();

        log.debug("Gemini model call with functions completed bean={} durationMs={} responseLength={}",
                chatModel.getClass().getSimpleName(),
                Math.max(0, (System.nanoTime() - startedAt) / 1_000_000L),
                lengthOf(content));
        return withUsageFrom(chatResponse,
                new AiModelResponse(content, modelName(), false, PROVIDER_NAME, AiModelResponse.providerCodeFor(PROVIDER_NAME)));
    }

    private AiModelResponse withUsageFrom(ChatResponse chatResponse, AiModelResponse response) {
        try {
            var usage = chatResponse == null || chatResponse.getMetadata() == null
                    ? null
                    : chatResponse.getMetadata().getUsage();
            if (usage == null) {
                return response;
            }
            int promptTokens = usage.getPromptTokens() == null ? 0 : usage.getPromptTokens().intValue();
            int completionTokens = usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens().intValue();
            Integer total = usage.getTotalTokens();
            int totalTokens = total == null || total == 0 ? promptTokens + completionTokens : total;
            return response.withUsage(promptTokens, completionTokens, totalTokens);
        } catch (RuntimeException exception) {
            log.debug("Gemini usage metadata unavailable: {}", exception.getMessage());
            return response;
        }
    }

    private AiModelResponse fallbackResponse(String message) {
        return new AiModelResponse(message, modelName(), true, PROVIDER_NAME, AiModelResponse.providerCodeFor(PROVIDER_NAME));
    }

    private int timeoutMs() {
        if (aiProperties.getRequestTimeoutMs() > 0) {
            return aiProperties.getRequestTimeoutMs();
        }
        return Math.max(1, aiProperties.getTimeoutSeconds()) * 1_000;
    }

    private String modelName() {
        String geminiModel = aiProperties.getGemini() == null ? null : aiProperties.getGemini().getModel();
        if (geminiModel != null && !geminiModel.isBlank()) {
            return geminiModel;
        }
        return aiProperties.getModel();
    }

    private static int lengthOf(String value) {
        return value == null ? 0 : value.length();
    }

    private static Throwable unwrap(Throwable exception) {
        Throwable current = exception;
        while ((current instanceof CompletionException || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static String exceptionCategory(Throwable exception) {
        return exception instanceof TimeoutException ? "timeout" : exception.getClass().getSimpleName();
    }

    private static String safeDetail(Throwable exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message.length() > 180 ? message.substring(0, 180) + "..." : message;
    }
}
