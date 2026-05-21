package com.example.valueinsoftbackend.ai.service;

import java.util.List;
import org.springframework.ai.tool.ToolCallback;

public interface AiModelClient {

    AiModelResponse generate(AiModelRequest request);

    default AiModelResponse generateWithFunctions(AiModelRequest request, List<ToolCallback> functions) {
        return generate(request);
    }

    default reactor.core.publisher.Flux<org.springframework.ai.chat.model.ChatResponse> streamWithFunctions(
            AiModelRequest request,
            List<ToolCallback> functions) {
        throw new UnsupportedOperationException("Streaming is not supported by this client.");
    }
}
