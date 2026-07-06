package com.example.valueinsoftbackend.ai.audit;

import com.example.valueinsoftbackend.ai.config.AiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
@Slf4j
public class AiUsageLogService {

    private static final BigDecimal ONE_MILLION = BigDecimal.valueOf(1_000_000L);
    private static final int USD_SCALE = 8;
    private static final int EGP_SCALE = 6;

    private final AiUsageLogRepository repository;
    private final AiProperties aiProperties;
    private final AiUsageMeteringContext usageMeteringContext;

    public AiUsageLogService(AiUsageLogRepository repository,
                             AiProperties aiProperties,
                             AiUsageMeteringContext usageMeteringContext) {
        this.repository = repository;
        this.aiProperties = aiProperties;
        this.usageMeteringContext = usageMeteringContext;
    }

    /**
     * Writes one metered usage row for the request that just finished. Token counts
     * are the totals accumulated from every provider call made while serving it.
     *
     * <p>DeepSeek is priced per token type (cache-miss input / cache-hit input /
     * output) in USD and converted to EGP at the configured rate. When the provider
     * does not report cached tokens, all input tokens are billed as cache misses.
     */
    public void logChatUsage(long companyId,
                             long userId,
                             UUID conversationId,
                             Long durationMs) {
        AiUsageMeteringContext.Usage usage = usageMeteringContext.consume();
        try {
            String modelName = usage.getModelName().isBlank() ? aiProperties.getModel() : usage.getModelName();
            BigDecimal costUsd = billableCostUsd(modelName, usage);
            BigDecimal costEgp = toEgp(modelName, costUsd, usage);
            repository.create(
                    companyId,
                    userId,
                    conversationId,
                    modelName,
                    usage.getPromptTokens(),
                    usage.getCompletionTokens(),
                    usage.getTotalTokens(),
                    usage.getCachedPromptTokens(),
                    costEgp,
                    costUsd,
                    durationMs
            );
        } catch (RuntimeException exception) {
            log.warn("Failed to write AI usage log row for conversation {}", conversationId, exception);
        }
    }

    private boolean isDeepseek(String modelName) {
        return modelName != null && modelName.trim().toLowerCase().contains("deepseek");
    }

    private AiProperties.UsageBillingProperties billing() {
        return aiProperties.getUsageBilling() == null
                ? new AiProperties.UsageBillingProperties()
                : aiProperties.getUsageBilling();
    }

    private BigDecimal billableCostUsd(String modelName, AiUsageMeteringContext.Usage usage) {
        if (usage.getTotalTokens() <= 0) {
            return BigDecimal.ZERO;
        }
        AiProperties.UsageBillingProperties billing = billing();

        if (isDeepseek(modelName)) {
            // cachedInputTokens capped at total input; remainder billed as cache miss.
            int cachedInputTokens = Math.min(Math.max(0, usage.getCachedPromptTokens()), usage.getPromptTokens());
            int nonCachedInputTokens = usage.getPromptTokens() - cachedInputTokens;
            int outputTokens = usage.getCompletionTokens();

            BigDecimal nonCachedInputCostUsd = perMillion(nonCachedInputTokens, billing.getDeepseekInputUsdPerMillionTokens());
            BigDecimal cachedInputCostUsd = perMillion(cachedInputTokens, billing.getDeepseekCachedInputUsdPerMillionTokens());
            BigDecimal outputCostUsd = perMillion(outputTokens, billing.getDeepseekOutputUsdPerMillionTokens());
            return nonCachedInputCostUsd.add(cachedInputCostUsd).add(outputCostUsd)
                    .setScale(USD_SCALE, RoundingMode.HALF_UP);
        }

        // Non-DeepSeek models keep the flat EGP rate; derive USD from the EGP amount.
        BigDecimal egp = flatEgpCost(modelName, usage.getTotalTokens());
        BigDecimal rate = billing.getUsdToEgpRate();
        if (egp.compareTo(BigDecimal.ZERO) <= 0 || rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return egp.divide(rate, USD_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal toEgp(String modelName, BigDecimal costUsd, AiUsageMeteringContext.Usage usage) {
        if (isDeepseek(modelName)) {
            BigDecimal rate = billing().getUsdToEgpRate();
            if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
                return BigDecimal.ZERO;
            }
            return costUsd.multiply(rate).setScale(EGP_SCALE, RoundingMode.HALF_UP);
        }
        return flatEgpCost(modelName, usage.getTotalTokens());
    }

    private BigDecimal flatEgpCost(String modelName, int totalTokens) {
        if (totalTokens <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal pricePerMillion = billing().priceForModel(modelName);
        if (pricePerMillion == null || pricePerMillion.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return pricePerMillion
                .multiply(BigDecimal.valueOf(totalTokens))
                .divide(ONE_MILLION, EGP_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal perMillion(int tokens, BigDecimal usdPerMillion) {
        if (tokens <= 0 || usdPerMillion == null || usdPerMillion.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return usdPerMillion
                .multiply(BigDecimal.valueOf(tokens))
                .divide(ONE_MILLION, USD_SCALE, RoundingMode.HALF_UP);
    }
}
