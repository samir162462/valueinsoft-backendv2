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
    private int maxOutputTokens = 2000;
    private boolean thinkingEnabled = true;
    private int timeoutSeconds = 30;
    private long monthlyTokenLimitDefault = 1_000_000L;
    private int dailyUserRequestLimit = 100;
    private boolean ragEnabled = false;
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
    private RagProperties rag = new RagProperties();
    private EmbeddingProperties embedding = new EmbeddingProperties();
    private GeminiProperties gemini = new GeminiProperties();
    private DeepSeekProperties deepseek = new DeepSeekProperties();
    private UsageBillingProperties usageBilling = new UsageBillingProperties();

    public UsageBillingProperties getUsageBilling() {
        return usageBilling;
    }

    public void setUsageBilling(UsageBillingProperties usageBilling) {
        this.usageBilling = usageBilling;
    }

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

    public boolean isThinkingEnabled() {
        return thinkingEnabled;
    }

    public void setThinkingEnabled(boolean thinkingEnabled) {
        this.thinkingEnabled = thinkingEnabled;
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
        return rag == null ? ragEnabled : rag.isEnabled();
    }

    public void setRagEnabled(boolean ragEnabled) {
        this.ragEnabled = ragEnabled;
        if (this.rag == null) {
            this.rag = new RagProperties();
        }
        this.rag.setEnabled(ragEnabled);
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

    public RagProperties getRag() {
        return rag;
    }

    public void setRag(RagProperties rag) {
        this.rag = rag == null ? new RagProperties() : rag;
        this.ragEnabled = this.rag.isEnabled();
    }

    public EmbeddingProperties getEmbedding() {
        return embedding;
    }

    public void setEmbedding(EmbeddingProperties embedding) {
        this.embedding = embedding == null ? new EmbeddingProperties() : embedding;
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

    public static class RagProperties {
        private boolean enabled = false;
        private int topK = 5;
        private double similarityThreshold = 0.60;
        private int chunkTargetTokens = 700;
        private int chunkOverlapTokens = 120;
        private boolean keywordFallbackEnabled = true;
        private String defaultLanguage = "en";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getTopK() {
            return topK;
        }

        public void setTopK(int topK) {
            this.topK = topK;
        }

        public double getSimilarityThreshold() {
            return similarityThreshold;
        }

        public void setSimilarityThreshold(double similarityThreshold) {
            this.similarityThreshold = similarityThreshold;
        }

        public int getChunkTargetTokens() {
            return chunkTargetTokens;
        }

        public void setChunkTargetTokens(int chunkTargetTokens) {
            this.chunkTargetTokens = chunkTargetTokens;
        }

        public int getChunkOverlapTokens() {
            return chunkOverlapTokens;
        }

        public void setChunkOverlapTokens(int chunkOverlapTokens) {
            this.chunkOverlapTokens = chunkOverlapTokens;
        }

        public boolean isKeywordFallbackEnabled() {
            return keywordFallbackEnabled;
        }

        public void setKeywordFallbackEnabled(boolean keywordFallbackEnabled) {
            this.keywordFallbackEnabled = keywordFallbackEnabled;
        }

        public String getDefaultLanguage() {
            return defaultLanguage;
        }

        public void setDefaultLanguage(String defaultLanguage) {
            this.defaultLanguage = defaultLanguage;
        }
    }

    public static class EmbeddingProperties {
        private boolean enabled = true;
        private String provider = "google";
        private String model = "gemini-embedding-2";
        private int dimension = 768;
        private GoogleEmbeddingProperties google = new GoogleEmbeddingProperties();

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

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public int getDimension() {
            return dimension;
        }

        public void setDimension(int dimension) {
            this.dimension = dimension;
        }

        public GoogleEmbeddingProperties getGoogle() {
            return google;
        }

        public void setGoogle(GoogleEmbeddingProperties google) {
            this.google = google == null ? new GoogleEmbeddingProperties() : google;
        }
    }

    public static class GoogleEmbeddingProperties {
        private String apiKey = "";
        private String baseUrl = "https://generativelanguage.googleapis.com/v1beta";
        private int timeoutMs = 60_000;
        private int batchSize = 100;

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

        public int getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
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

    /**
     * Client-facing usage billing rates (market-practice metered billing).
     * Prices are what the CLIENT pays per one million tokens, in EGP, and
     * should include the margin over the raw provider cost.
     */
    public static class UsageBillingProperties {
        private boolean enabled = true;
        private java.math.BigDecimal defaultPricePerMillionTokensEgp = new java.math.BigDecimal("100.00");
        private java.math.BigDecimal deepseekPricePerMillionTokensEgp = new java.math.BigDecimal("60.00");
        private java.math.BigDecimal geminiPricePerMillionTokensEgp = new java.math.BigDecimal("120.00");
        private java.math.BigDecimal minimumMonthlyChargeEgp = java.math.BigDecimal.ZERO;
        // DeepSeek token-type pricing in USD per one million tokens.
        private java.math.BigDecimal deepseekInputUsdPerMillionTokens = new java.math.BigDecimal("0.14");
        private java.math.BigDecimal deepseekCachedInputUsdPerMillionTokens = new java.math.BigDecimal("0.0028");
        private java.math.BigDecimal deepseekOutputUsdPerMillionTokens = new java.math.BigDecimal("0.28");
        private java.math.BigDecimal usdToEgpRate = new java.math.BigDecimal("48.85");

        public java.math.BigDecimal getDeepseekInputUsdPerMillionTokens() {
            return deepseekInputUsdPerMillionTokens;
        }

        public void setDeepseekInputUsdPerMillionTokens(java.math.BigDecimal deepseekInputUsdPerMillionTokens) {
            this.deepseekInputUsdPerMillionTokens = deepseekInputUsdPerMillionTokens;
        }

        public java.math.BigDecimal getDeepseekCachedInputUsdPerMillionTokens() {
            return deepseekCachedInputUsdPerMillionTokens;
        }

        public void setDeepseekCachedInputUsdPerMillionTokens(java.math.BigDecimal deepseekCachedInputUsdPerMillionTokens) {
            this.deepseekCachedInputUsdPerMillionTokens = deepseekCachedInputUsdPerMillionTokens;
        }

        public java.math.BigDecimal getDeepseekOutputUsdPerMillionTokens() {
            return deepseekOutputUsdPerMillionTokens;
        }

        public void setDeepseekOutputUsdPerMillionTokens(java.math.BigDecimal deepseekOutputUsdPerMillionTokens) {
            this.deepseekOutputUsdPerMillionTokens = deepseekOutputUsdPerMillionTokens;
        }

        public java.math.BigDecimal getUsdToEgpRate() {
            return usdToEgpRate;
        }

        public void setUsdToEgpRate(java.math.BigDecimal usdToEgpRate) {
            this.usdToEgpRate = usdToEgpRate;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public java.math.BigDecimal getDefaultPricePerMillionTokensEgp() {
            return defaultPricePerMillionTokensEgp;
        }

        public void setDefaultPricePerMillionTokensEgp(java.math.BigDecimal defaultPricePerMillionTokensEgp) {
            this.defaultPricePerMillionTokensEgp = defaultPricePerMillionTokensEgp;
        }

        public java.math.BigDecimal getDeepseekPricePerMillionTokensEgp() {
            return deepseekPricePerMillionTokensEgp;
        }

        public void setDeepseekPricePerMillionTokensEgp(java.math.BigDecimal deepseekPricePerMillionTokensEgp) {
            this.deepseekPricePerMillionTokensEgp = deepseekPricePerMillionTokensEgp;
        }

        public java.math.BigDecimal getGeminiPricePerMillionTokensEgp() {
            return geminiPricePerMillionTokensEgp;
        }

        public void setGeminiPricePerMillionTokensEgp(java.math.BigDecimal geminiPricePerMillionTokensEgp) {
            this.geminiPricePerMillionTokensEgp = geminiPricePerMillionTokensEgp;
        }

        public java.math.BigDecimal getMinimumMonthlyChargeEgp() {
            return minimumMonthlyChargeEgp;
        }

        public void setMinimumMonthlyChargeEgp(java.math.BigDecimal minimumMonthlyChargeEgp) {
            this.minimumMonthlyChargeEgp = minimumMonthlyChargeEgp;
        }

        public java.math.BigDecimal priceForModel(String modelName) {
            String normalized = modelName == null ? "" : modelName.trim().toLowerCase();
            if (normalized.contains("deepseek")) {
                return deepseekPricePerMillionTokensEgp;
            }
            if (normalized.contains("gemini")) {
                return geminiPricePerMillionTokensEgp;
            }
            return defaultPricePerMillionTokensEgp;
        }
    }
}
