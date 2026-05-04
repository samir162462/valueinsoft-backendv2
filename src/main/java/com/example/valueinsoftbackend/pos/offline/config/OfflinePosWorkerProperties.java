package com.example.valueinsoftbackend.pos.offline.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "valueinsoft.pos.offline.worker")
public class OfflinePosWorkerProperties {

    private boolean enabled = false;
    private boolean processingEnabled = false;
    private boolean validationEnabled = false;
    private boolean postingEnabled = false;
    private int batchSize = 25;
    private long fixedDelayMs = 30000;
    private int stuckThresholdMinutes = 15;
    /**
     * Explicit allowlist of tenant/branch targets in the form "companyId:branchId",
     * separated by commas. The worker does not discover tenants by scanning schemas.
     */
    private String targets = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isProcessingEnabled() {
        return processingEnabled;
    }

    public void setProcessingEnabled(boolean processingEnabled) {
        this.processingEnabled = processingEnabled;
    }

    public boolean isValidationEnabled() {
        return validationEnabled;
    }

    public void setValidationEnabled(boolean validationEnabled) {
        this.validationEnabled = validationEnabled;
    }

    public boolean isPostingEnabled() {
        return postingEnabled;
    }

    public void setPostingEnabled(boolean postingEnabled) {
        this.postingEnabled = postingEnabled;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public long getFixedDelayMs() {
        return fixedDelayMs;
    }

    public void setFixedDelayMs(long fixedDelayMs) {
        this.fixedDelayMs = fixedDelayMs;
    }

    public int getStuckThresholdMinutes() {
        return stuckThresholdMinutes;
    }

    public void setStuckThresholdMinutes(int stuckThresholdMinutes) {
        this.stuckThresholdMinutes = stuckThresholdMinutes;
    }

    public String getTargets() {
        return targets;
    }

    public void setTargets(String targets) {
        this.targets = targets;
    }
}
