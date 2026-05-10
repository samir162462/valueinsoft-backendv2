package com.example.valueinsoftbackend.ai.service;

import com.example.valueinsoftbackend.ai.config.AiProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
@ConditionalOnClass(ChatClient.class)
@ConditionalOnBean(ChatClient.Builder.class)
public class SpringAiChatModelClient implements AiModelClient {

    private final ChatClient chatClient;
    private final AiProperties aiProperties;

    public SpringAiChatModelClient(ChatClient.Builder chatClientBuilder, AiProperties aiProperties) {
        this.chatClient = chatClientBuilder.build();
        this.aiProperties = aiProperties;
    }

    @Override
    public AiModelResponse generate(AiModelRequest request) {
        String content = chatClient.prompt()
                .system(request.systemPrompt())
                .user(request.userMessage())
                .call()
                .content();
        return new AiModelResponse(content, aiProperties.getModel(), false);
    }
}
