package com.example.valueinsoftbackend.Config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@ConfigurationProperties(prefix = "fx.deepseek")
public class FxDeepSeekProperties {

    private Schedule schedule = new Schedule();
    private Initialization initialization = new Initialization();
    private Retry retry = new Retry();
    private String endpointUrl = "https://open.er-api.com/v6/latest/{baseCurrency}";
    private int timeoutMs = 20_000;
    private String baseCurrency = "USD";
    private String targetCurrency = "EGP";
    private BigDecimal minimumRate = new BigDecimal("10.00000000");
    private BigDecimal maximumRate = new BigDecimal("150.00000000");
    private BigDecimal maximumChangePercentage = null; // disabled – set FX_DEEPSEEK_MAXIMUM_CHANGE_PERCENTAGE to re-enable
    private BigDecimal minimumConfidence = new BigDecimal("0.5000");
    private int companyBatchSize = 25;
    private int lockAtMostMinutes = 30;
    private int recommendationMetricsWindowDays = 30;
    private int recommendationMaxProducts = 100;
    private String sourceCode = "EXCHANGE_RATE_API";

    public Schedule getSchedule() {
        return schedule;
    }

    public void setSchedule(Schedule schedule) {
        this.schedule = schedule == null ? new Schedule() : schedule;
    }

    public Initialization getInitialization() {
        return initialization;
    }

    public void setInitialization(Initialization initialization) {
        this.initialization = initialization == null ? new Initialization() : initialization;
    }

    public Retry getRetry() {
        return retry;
    }

    public void setRetry(Retry retry) {
        this.retry = retry == null ? new Retry() : retry;
    }

    public String getEndpointUrl() {
        return endpointUrl;
    }

    public void setEndpointUrl(String endpointUrl) {
        this.endpointUrl = endpointUrl;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public String getBaseCurrency() {
        return baseCurrency;
    }

    public void setBaseCurrency(String baseCurrency) {
        this.baseCurrency = baseCurrency;
    }

    public String getTargetCurrency() {
        return targetCurrency;
    }

    public void setTargetCurrency(String targetCurrency) {
        this.targetCurrency = targetCurrency;
    }

    public BigDecimal getMinimumRate() {
        return minimumRate;
    }

    public void setMinimumRate(BigDecimal minimumRate) {
        this.minimumRate = minimumRate;
    }

    public BigDecimal getMaximumRate() {
        return maximumRate;
    }

    public void setMaximumRate(BigDecimal maximumRate) {
        this.maximumRate = maximumRate;
    }

    public BigDecimal getMaximumChangePercentage() {
        return maximumChangePercentage;
    }

    public void setMaximumChangePercentage(BigDecimal maximumChangePercentage) {
        this.maximumChangePercentage = maximumChangePercentage;
    }

    public BigDecimal getMinimumConfidence() {
        return minimumConfidence;
    }

    public void setMinimumConfidence(BigDecimal minimumConfidence) {
        this.minimumConfidence = minimumConfidence;
    }

    public int getCompanyBatchSize() {
        return companyBatchSize;
    }

    public void setCompanyBatchSize(int companyBatchSize) {
        this.companyBatchSize = companyBatchSize;
    }

    public int getLockAtMostMinutes() {
        return lockAtMostMinutes;
    }

    public void setLockAtMostMinutes(int lockAtMostMinutes) {
        this.lockAtMostMinutes = lockAtMostMinutes;
    }

    public int getRecommendationMetricsWindowDays() {
        return recommendationMetricsWindowDays;
    }

    public void setRecommendationMetricsWindowDays(int recommendationMetricsWindowDays) {
        this.recommendationMetricsWindowDays = recommendationMetricsWindowDays;
    }

    public int getRecommendationMaxProducts() {
        return recommendationMaxProducts;
    }

    public void setRecommendationMaxProducts(int recommendationMaxProducts) {
        this.recommendationMaxProducts = recommendationMaxProducts;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
    }

    public static class Schedule {
        private boolean enabled = false;
        private String cron = "0 0 2 * * *";
        private String timeZone = "Africa/Cairo";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getCron() {
            return cron;
        }

        public void setCron(String cron) {
            this.cron = cron;
        }

        public String getTimeZone() {
            return timeZone;
        }

        public void setTimeZone(String timeZone) {
            this.timeZone = timeZone;
        }
    }

    public static class Initialization {
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Retry {
        private int maxAttempts = 3;
        private int initialDelaySeconds = 30;
        private BigDecimal multiplier = new BigDecimal("2.0000");

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public int getInitialDelaySeconds() {
            return initialDelaySeconds;
        }

        public void setInitialDelaySeconds(int initialDelaySeconds) {
            this.initialDelaySeconds = initialDelaySeconds;
        }

        public BigDecimal getMultiplier() {
            return multiplier;
        }

        public void setMultiplier(BigDecimal multiplier) {
            this.multiplier = multiplier;
        }
    }
}
