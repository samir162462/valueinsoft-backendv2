package com.example.valueinsoftbackend.ai.provider;

import com.example.valueinsoftbackend.ai.service.AiModelRequest;
import com.example.valueinsoftbackend.ai.service.AiModelResponse;

public interface AiProvider {

    String getName();

    AiModelResponse generate(AiModelRequest request);
}
