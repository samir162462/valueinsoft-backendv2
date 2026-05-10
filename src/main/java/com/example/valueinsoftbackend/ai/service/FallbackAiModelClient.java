package com.example.valueinsoftbackend.ai.service;

import com.example.valueinsoftbackend.ai.config.AiProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnMissingBean(AiModelClient.class)
public class FallbackAiModelClient implements AiModelClient {

    private final AiProperties aiProperties;

    public FallbackAiModelClient(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
    }

    @Override
    public AiModelResponse generate(AiModelRequest request) {
        String answer = "I can help with general ValueInSoft usage. Business data tools, chat history, and knowledge base search are not enabled in this phase yet.";
        return new AiModelResponse(answer, aiProperties.getModel(), true);
    }
}
