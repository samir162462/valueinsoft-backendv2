package com.example.valueinsoftbackend.ai.service;

import com.example.valueinsoftbackend.ai.provider.GeminiAiProvider;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@ConditionalOnClass(ChatModel.class)
@Slf4j
public class SpringAiChatModelClient implements AiModelClient {

    private final GeminiAiProvider geminiAiProvider;

    public SpringAiChatModelClient(GeminiAiProvider geminiAiProvider) {
        this.geminiAiProvider = geminiAiProvider;
        log.info("SpringAiChatModelClient initialized as Gemini compatibility delegate");
    }

    @Override
    public AiModelResponse generate(AiModelRequest request) {
        return geminiAiProvider.generate(request);
    }

    @Override
    public AiModelResponse generateWithFunctions(AiModelRequest request, List<ToolCallback> functions) {
        return geminiAiProvider.generateWithFunctions(request, functions);
    }

    @Override
    public Flux<ChatResponse> streamWithFunctions(AiModelRequest request, List<ToolCallback> functions) {
        return geminiAiProvider.streamWithFunctions(request, functions);
    }
}
