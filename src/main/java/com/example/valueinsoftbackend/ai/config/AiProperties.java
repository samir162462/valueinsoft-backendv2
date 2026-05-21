package com.example.valueinsoftbackend.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vls.ai")
public class AiProperties {

    private boolean enabled = false;
    private String provider = "google";
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
}
