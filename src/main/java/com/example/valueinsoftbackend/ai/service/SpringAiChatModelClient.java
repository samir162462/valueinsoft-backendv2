package com.example.valueinsoftbackend.ai.service;

import com.example.valueinsoftbackend.ai.config.AiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
@Primary
@ConditionalOnClass(ChatModel.class)
@Slf4j
public class SpringAiChatModelClient implements AiModelClient {

    private final ChatModel chatModel;
    private final AiProperties aiProperties;

    public SpringAiChatModelClient(Map<String, ChatModel> chatModels, AiProperties aiProperties) {
        this.aiProperties = aiProperties;
        String provider = normalizeProvider(aiProperties.getProvider());
        
        // Match the provider to the available beans
        // Spring AI bean names usually follow the pattern [provider]ChatModel
        this.chatModel = chatModels.entrySet().stream()
                .filter(entry -> entry.getKey().toLowerCase().contains(provider))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseGet(() -> {
                    log.warn("No ChatModel found for provider '{}'. Using first available bean.", provider);
                    return chatModels.values().stream().findFirst().orElse(null);
                });

        if (this.chatModel != null) {
            log.info("Initializing SpringAiChatModelClient with {} provider (Model: {})", 
                    provider, this.chatModel.getClass().getSimpleName());
        } else {
            log.error("No ChatModel bean found in context. AI chat will fail.");
        }
    }

    private String normalizeProvider(String provider) {
        String normalized = provider == null ? "" : provider.trim().toLowerCase();
        if (normalized.equals("gemini") || normalized.equals("genai") || normalized.equals("google-genai")) {
            return "google";
        }
        return normalized.isBlank() ? "google" : normalized;
    }

    @Override
    public AiModelResponse generate(AiModelRequest request) {
        if (chatModel == null) {
            return new AiModelResponse("AI engine not loaded correctly.", aiProperties.getModel(), true);
        }
        
        try {
            return CompletableFuture.supplyAsync(() -> callModel(request))
                    .orTimeout(Math.max(1, aiProperties.getTimeoutSeconds()), TimeUnit.SECONDS)
                    .exceptionally(exception -> {
                        log.error("AI model call failed: {}", exception.getMessage(), exception);
                        return new AiModelResponse(
                            "AI response timed out or is temporarily unavailable. Try again shortly.",
                            aiProperties.getModel(),
                            true
                        );
                    })
                    .join();
        } catch (RuntimeException exception) {
            log.error("AI model call runtime exception: {}", exception.getMessage(), exception);
            return new AiModelResponse(
                    "AI response timed out or is temporarily unavailable. Try again shortly.",
                    aiProperties.getModel(),
                    true
            );
        }
    }

    private AiModelResponse callModel(AiModelRequest request) {
        String userMessageContent = buildUserMessageContent(request);

        SystemMessage systemMessage = new SystemMessage(request.systemPrompt());
        UserMessage userMessage = new UserMessage(userMessageContent);
        
        String content = chatModel.call(new Prompt(List.of(systemMessage, userMessage)))
                .getResult()
                .getOutput()
                .getText();
                
        return new AiModelResponse(content, aiProperties.getModel(), false);
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
}
