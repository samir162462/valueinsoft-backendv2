package com.example.valueinsoftbackend.ai.service;

import com.example.valueinsoftbackend.ai.provider.AiProviderException;
import org.springframework.stereotype.Service;

@Service
public class FallbackAiModelClient implements AiModelClient {

    @Override
    public AiModelResponse generate(AiModelRequest request) {
        throw new AiProviderException(
                AiProviderException.Category.PROVIDER_BAD_RESPONSE,
                "fallback",
                "No real AI provider is available."
        );
    }
}
