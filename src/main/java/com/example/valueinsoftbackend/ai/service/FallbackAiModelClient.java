package com.example.valueinsoftbackend.ai.service;

import com.example.valueinsoftbackend.ai.config.AiProperties;
import org.springframework.stereotype.Service;

@Service
public class FallbackAiModelClient implements AiModelClient {

    private final AiProperties aiProperties;

    public FallbackAiModelClient(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
    }

    @Override
    public AiModelResponse generate(AiModelRequest request) {
        String answer = request.knowledgeContext() == null || request.knowledgeContext().isBlank()
                ? "I can help with general ValueInSoft usage. A matching internal help article is not available yet."
                : "Based on the internal help article: " + request.knowledgeContext();
        return new AiModelResponse(answer, aiProperties.getModel(), true);
    }
}
