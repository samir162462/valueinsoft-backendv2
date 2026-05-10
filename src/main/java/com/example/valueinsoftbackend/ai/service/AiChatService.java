package com.example.valueinsoftbackend.ai.service;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.ai.config.AiProperties;
import com.example.valueinsoftbackend.ai.dto.AiChatRequest;
import com.example.valueinsoftbackend.ai.dto.AiChatResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.UUID;

@Service
public class AiChatService {

    private final AiProperties aiProperties;
    private final AiPromptPolicyService promptPolicyService;
    private final AiChatOrchestratorService orchestratorService;

    public AiChatService(AiProperties aiProperties,
                         AiPromptPolicyService promptPolicyService,
                         AiChatOrchestratorService orchestratorService) {
        this.aiProperties = aiProperties;
        this.promptPolicyService = promptPolicyService;
        this.orchestratorService = orchestratorService;
    }

    public AiChatResponse chat(AiChatRequest request, Principal principal) {
        if (!aiProperties.isEnabled()) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI_DISABLED",
                    "AI assistant is disabled"
            );
        }

        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required");
        }

        String normalizedMode = promptPolicyService.normalizeMode(request.mode());
        String conversationId = normalizeConversationId(request.conversationId());
        AiChatOrchestratorService.OrchestratedChatResult result = orchestratorService.answer(request, normalizedMode);

        return new AiChatResponse(
                conversationId,
                result.answer(),
                normalizedMode,
                result.suggestedQuestions(),
                result.actions(),
                result.sources(),
                result.toolCalls()
        );
    }

    private String normalizeConversationId(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        try {
            return UUID.fromString(conversationId.trim()).toString();
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CONVERSATION_ID", "Invalid conversation id");
        }
    }
}
