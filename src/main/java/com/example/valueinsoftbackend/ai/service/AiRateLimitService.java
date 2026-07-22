package com.example.valueinsoftbackend.ai.service;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.ai.audit.AiRateLimitRepository;
import com.example.valueinsoftbackend.ai.config.AiProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AiRateLimitService {

    private final AiProperties aiProperties;
    private final AiRateLimitRepository rateLimitRepository;

    public AiRateLimitService(AiProperties aiProperties, AiRateLimitRepository rateLimitRepository) {
        this.aiProperties = aiProperties;
        this.rateLimitRepository = rateLimitRepository;
    }

    public void validateDailyUserRequestLimit(AiSecurityContext context) {
        int limit = aiProperties.getDailyUserRequestLimit();
        if (limit <= 0) {
            return;
        }

        if (!rateLimitRepository.tryConsumeDailyUserRequest(context.companyId(), context.userId(), limit)) {
            throw new ApiException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "AI_DAILY_USER_LIMIT_EXCEEDED",
                    "AI daily request limit reached"
            );
        }
    }
}
