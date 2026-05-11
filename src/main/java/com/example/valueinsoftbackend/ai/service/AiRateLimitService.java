package com.example.valueinsoftbackend.ai.service;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.ai.audit.AiUsageLogRepository;
import com.example.valueinsoftbackend.ai.config.AiProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class AiRateLimitService {

    private final AiProperties aiProperties;
    private final AiUsageLogRepository usageLogRepository;

    public AiRateLimitService(AiProperties aiProperties, AiUsageLogRepository usageLogRepository) {
        this.aiProperties = aiProperties;
        this.usageLogRepository = usageLogRepository;
    }

    public void validateDailyUserRequestLimit(AiSecurityContext context) {
        int limit = aiProperties.getDailyUserRequestLimit();
        if (limit <= 0) {
            return;
        }

        long usedToday = usageLogRepository.countUserRequestsSince(
                context.companyId(),
                context.userId(),
                LocalDate.now().atStartOfDay()
        );
        if (usedToday >= limit) {
            throw new ApiException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "AI_DAILY_USER_LIMIT_EXCEEDED",
                    "AI daily request limit reached"
            );
        }
    }
}
