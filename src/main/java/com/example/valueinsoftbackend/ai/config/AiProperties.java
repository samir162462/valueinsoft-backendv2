package com.example.valueinsoftbackend.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vls.ai")
public class AiProperties {

    private boolean enabled = false;
    private String provider = "deepseek";
    private String fallbackProvider = "gemini";
    private boolean fallbackEnabled = true;
    private int requestTimeoutMs = 60_000;
    private String model = "gemini-2.5-flash";
    private double temperature = 0.2;
    private int maxOutputTokens = 800;
    private int timeoutSeconds = 30;
    private long monthlyTokenLimitDefault = 1_000_000L;
    private int dailyUserRequestLimit = 100;
    private boolean ragEnabled = true;
    private boolean toolsEnabled = true;
    private boolean writeActionsEnabled = false;
    private boolean sqlAgentEnabled = true;
    private int sqlQueryTimeoutSeconds = 5;
    private int sqlMaxRows = 100;
    private int maxResultRows = 50;
    private int maxSchemaTables = 8;
    private int maxColumnsPerTable = 20;
    private int cacheTtlMinutes = 30;
    private boolean streamingEnabled = true;
    private boolean functionCallingEnabled = true;
    private GeminiProperties gemini = new GeminiProperties();
    private DeepSeekProperties deepseek = new DeepSeekProperties();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getFallbackProvider() {
        return fallbackProvider;
    }

    public void setFallbackProvider(String fallbackProvider) {
        this.fallbackProvider = fallbackProvider;
    }

    public boolean isFallbackEnabled() {
        return fallbackEnabled;
    }

    public void setFallbackEnabled(boolean fallbackEnabled) {
        this.fallbackEnabled = fallbackEnabled;
    }

    public int getRequestTimeoutMs() {
        return requestTimeoutMs;
    }

    public void setRequestTimeoutMs(int requestTimeoutMs) {
        this.requestTimeoutMs = requestTimeoutMs;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public int getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public void setMaxOutputTokens(int maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public long getMonthlyTokenLimitDefault() {
        return monthlyTokenLimitDefault;
    }

    public void setMonthlyTokenLimitDefault(long monthlyTokenLimitDefault) {
        this.monthlyTokenLimitDefault = monthlyTokenLimitDefault;
    }

    public int getDailyUserRequestLimit() {
        return dailyUserRequestLimit;
    }

    public void setDailyUserRequestLimit(int dailyUserRequestLimit) {
        this.dailyUserRequestLimit = dailyUserRequestLimit;
    }

    public boolean isRagEnabled() {
        return ragEnabled;
    }

    public void setRagEnabled(boolean ragEnabled) {
        this.ragEnabled = ragEnabled;
    }

    public boolean isToolsEnabled() {
        return toolsEnabled;
    }

    public void setToolsEnabled(boolean toolsEnabled) {
        this.toolsEnabled = toolsEnabled;
    }

    public boolean isWriteActionsEnabled() {
        return writeActionsEnabled;
    }

    public void setWriteActionsEnabled(boolean writeActionsEnabled) {
        this.writeActionsEnabled = writeActionsEnabled;
    }

    public boolean isSqlAgentEnabled() {
        return sqlAgentEnabled;
    }

    public void setSqlAgentEnabled(boolean sqlAgentEnabled) {
        this.sqlAgentEnabled = sqlAgentEnabled;
    }

    public int getSqlQueryTimeoutSeconds() {
        return sqlQueryTimeoutSeconds;
    }

    public void setSqlQueryTimeoutSeconds(int sqlQueryTimeoutSeconds) {
        this.sqlQueryTimeoutSeconds = sqlQueryTimeoutSeconds;
    }

    public int getSqlMaxRows() {
        return sqlMaxRows;
    }

    public void setSqlMaxRows(int sqlMaxRows) {
        this.sqlMaxRows = sqlMaxRows;
    }

    public int getMaxResultRows() {
        return maxResultRows;
    }

    public void setMaxResultRows(int maxResultRows) {
        this.maxResultRows = maxResultRows;
    }

    public int getMaxSchemaTables() {
        return maxSchemaTables;
    }

    public void setMaxSchemaTables(int maxSchemaTables) {
        this.maxSchemaTables = maxSchemaTables;
    }

    public int getMaxColumnsPerTable() {
        return maxColumnsPerTable;
    }

    public void setMaxColumnsPerTable(int maxColumnsPerTable) {
        this.maxColumnsPerTable = maxColumnsPerTable;
    }

    public int getCacheTtlMinutes() {
        return cacheTtlMinutes;
    }

    public void setCacheTtlMinutes(int cacheTtlMinutes) {
        this.cacheTtlMinutes = cacheTtlMinutes;
    }

    public boolean isStreamingEnabled() {
        return streamingEnabled;
    }

    public void setStreamingEnabled(boolean streamingEnabled) {
        this.streamingEnabled = streamingEnabled;
    }

    public boolean isFunctionCallingEnabled() {
        return functionCallingEnabled;
    }

    public void setFunctionCallingEnabled(boolean functionCallingEnabled) {
        this.functionCallingEnabled = functionCallingEnabled;
    }

    public GeminiProperties getGemini() {
        return gemini;
    }

    public void setGemini(GeminiProperties gemini) {
        this.gemini = gemini;
    }

    public DeepSeekProperties getDeepseek() {
        return deepseek;
    }

    public void setDeepseek(DeepSeekProperties deepseek) {
        this.deepseek = deepseek;
    }

    public static class GeminiProperties {
        private String model = "gemini-2.5-flash";

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }
    }

    public static class DeepSeekProperties {
        private String apiKey = "";
        private String baseUrl = "https://api.deepseek.com";
        private String model = "deepseek-chat";
        private int timeoutMs = 60_000;

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public int getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
        }
    }
}
