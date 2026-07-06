package com.example.valueinsoftbackend.ai.audit;

import com.example.valueinsoftbackend.ai.service.AiModelResponse;
import org.springframework.stereotype.Component;

/**
 * Accumulates the token usage of every AI provider call made while serving the
 * current request thread. AiUsageLogService consumes (and clears) the total at
 * the end of the chat request, so exactly one metered row is written per request.
 */
@Component
public class AiUsageMeteringContext {

    public static final class Usage {
        private String modelName = "";
        private int promptTokens;
        private int completionTokens;
        private int totalTokens;
        private int cachedPromptTokens;

        public String getModelName() {
            return modelName;
        }

        public int getPromptTokens() {
            return promptTokens;
        }

        public int getCompletionTokens() {
            return completionTokens;
        }

        public int getTotalTokens() {
            return totalTokens;
        }

        public int getCachedPromptTokens() {
            return cachedPromptTokens;
        }
    }

    private static final ThreadLocal<Usage> CURRENT = new ThreadLocal<>();

    public void record(AiModelResponse response) {
        if (response == null || response.totalTokens() <= 0) {
            return;
        }
        Usage usage = CURRENT.get();
        if (usage == null) {
            usage = new Usage();
            CURRENT.set(usage);
        }
        // Keep the model name of the largest contributor so billing groups sensibly.
        if (usage.modelName.isBlank() || response.totalTokens() >= usage.totalTokens) {
            usage.modelName = response.modelName() == null ? "" : response.modelName();
        }
        usage.promptTokens += Math.max(0, response.promptTokens());
        usage.completionTokens += Math.max(0, response.completionTokens());
        usage.totalTokens += Math.max(0, response.totalTokens());
        usage.cachedPromptTokens += Math.max(0, response.cachedPromptTokens());
    }

    /** Returns the accumulated usage for this thread and clears it. Never null. */
    public Usage consume() {
        Usage usage = CURRENT.get();
        CURRENT.remove();
        return usage == null ? new Usage() : usage;
    }

    public void clear() {
        CURRENT.remove();
    }
}
