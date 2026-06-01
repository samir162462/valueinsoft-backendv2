package com.example.valueinsoftbackend.ai.embedding;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@ConditionalOnProperty(prefix = "vls.ai.embedding", name = "enabled", havingValue = "false", matchIfMissing = true)
public class DisabledAiEmbeddingProvider implements AiEmbeddingProvider {

    @Override
    public String providerName() {
        return "disabled";
    }

    @Override
    public String modelName() {
        return "";
    }

    @Override
    public int dimension() {
        return 0;
    }

    @Override
    public List<AiEmbeddingResult> embed(List<String> texts) {
        throw new AiEmbeddingException(
                "AI embeddings are disabled. Configure a real embedding provider before generating vectors."
        );
    }
}
