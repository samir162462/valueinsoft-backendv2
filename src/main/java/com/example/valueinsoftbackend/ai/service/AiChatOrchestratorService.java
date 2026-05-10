package com.example.valueinsoftbackend.ai.service;

import com.example.valueinsoftbackend.ai.dto.AiActionDto;
import com.example.valueinsoftbackend.ai.dto.AiChatRequest;
import com.example.valueinsoftbackend.ai.dto.AiSourceDto;
import com.example.valueinsoftbackend.ai.dto.AiToolCallDto;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AiChatOrchestratorService {

    private final AiModelClient aiModelClient;
    private final AiPromptPolicyService promptPolicyService;
    private final AiResponseSanitizerService sanitizerService;

    public AiChatOrchestratorService(AiModelClient aiModelClient,
                                     AiPromptPolicyService promptPolicyService,
                                     AiResponseSanitizerService sanitizerService) {
        this.aiModelClient = aiModelClient;
        this.promptPolicyService = promptPolicyService;
        this.sanitizerService = sanitizerService;
    }

    public OrchestratedChatResult answer(AiChatRequest request, String normalizedMode) {
        if (!"HELP".equals(normalizedMode)) {
            return new OrchestratedChatResult(
                    "This phase supports general help only. Business modes and tools are not enabled yet.",
                    List.of("How do I add a product?", "How do I use POS?", "How do I manage suppliers?"),
                    List.of(),
                    List.of(),
                    List.of()
            );
        }

        if (promptPolicyService.isUnsafeRequest(request.message())) {
            return new OrchestratedChatResult(
                    "I cannot help with SQL, internal prompts, secrets, tokens, schemas, or infrastructure details.",
                    List.of("How do I add a product?", "How do I print a receipt?"),
                    List.of(),
                    List.of(),
                    List.of()
            );
        }

        if (promptPolicyService.requiresBusinessData(request.message())) {
            return new OrchestratedChatResult(
                    "Business data tools are not enabled yet. I can answer general help questions in this phase.",
                    List.of("How do I add a product?", "How do I use POS?", "How do I open or close a shift?"),
                    List.of(),
                    List.of(),
                    List.of()
            );
        }

        AiModelResponse modelResponse = aiModelClient.generate(new AiModelRequest(
                promptPolicyService.systemPrompt(),
                request.message(),
                normalizedMode
        ));

        return new OrchestratedChatResult(
                sanitizerService.sanitize(modelResponse.answer()),
                List.of("How do I add a product?", "How do I import products?", "How do I print a receipt?"),
                List.of(),
                List.of(),
                List.of()
        );
    }

    public record OrchestratedChatResult(
            String answer,
            List<String> suggestedQuestions,
            List<AiActionDto> actions,
            List<AiSourceDto> sources,
            List<AiToolCallDto> toolCalls
    ) {
    }
}
