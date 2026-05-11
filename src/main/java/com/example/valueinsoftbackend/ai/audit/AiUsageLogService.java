package com.example.valueinsoftbackend.ai.audit;

import com.example.valueinsoftbackend.ai.config.AiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@Slf4j
public class AiUsageLogService {

    private final AiUsageLogRepository repository;
    private final AiProperties aiProperties;

    public AiUsageLogService(AiUsageLogRepository repository, AiProperties aiProperties) {
        this.repository = repository;
        this.aiProperties = aiProperties;
    }

    public void logChatUsage(long companyId,
                             long userId,
                             UUID conversationId,
                             Long durationMs) {
        try {
            repository.create(
                    companyId,
                    userId,
                    conversationId,
                    aiProperties.getModel(),
                    0,
                    0,
                    0,
                    BigDecimal.ZERO,
                    durationMs
            );
        } catch (RuntimeException exception) {
            log.warn("Failed to write AI usage log row for conversation {}", conversationId, exception);
        }
    }
}
