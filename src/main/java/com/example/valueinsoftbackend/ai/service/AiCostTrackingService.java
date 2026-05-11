package com.example.valueinsoftbackend.ai.service;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.ai.audit.AiUsageLogRepository;
import com.example.valueinsoftbackend.ai.config.AiProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class AiCostTrackingService {

    private final AiProperties aiProperties;
    private final AiUsageLogRepository usageLogRepository;

    public AiCostTrackingService(AiProperties aiProperties, AiUsageLogRepository usageLogRepository) {
        this.aiProperties = aiProperties;
        this.usageLogRepository = usageLogRepository;
    }

    public void validateCompanyMonthlyTokenLimit(AiSecurityContext context) {
        long limit = aiProperties.getMonthlyTokenLimitDefault();
        if (limit <= 0) {
            return;
        }

        LocalDate firstDayOfMonth = LocalDate.now().withDayOfMonth(1);
        long usedTokens = usageLogRepository.sumCompanyTokensSince(context.companyId(), firstDayOfMonth.atStartOfDay());
        if (usedTokens >= limit) {
            throw new ApiException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "AI_MONTHLY_COMPANY_LIMIT_EXCEEDED",
                    "AI monthly usage limit reached"
            );
        }
    }
}
