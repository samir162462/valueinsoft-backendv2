package com.example.valueinsoftbackend.pos.offline.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Externalised configuration for the offline POS sync module.
 * All properties are prefixed with {@code valueinsoft.pos.offline.*}
 * and can be overridden via application.properties or environment variables.
 */
@Configuration
@ConfigurationProperties(prefix = "valueinsoft.pos.offline")
public class OfflinePosProperties {

    private int maxOrdersPerBatch = 50;
    private int maxItemsPerOrder = 100;
    private int maxBootstrapPageSize = 500;
    private int maxOfflineHoursDefault = 24;
    private boolean allowOfflineSync = true;
    private boolean runTenantMigrationOnStartup = false;
    private Worker worker = new Worker();

    // -------------------------------------------------------
    // Getters / Setters (required for @ConfigurationProperties binding)
    // -------------------------------------------------------

    public int getMaxOrdersPerBatch() {
        return maxOrdersPerBatch;
    }

    public void setMaxOrdersPerBatch(int maxOrdersPerBatch) {
        this.maxOrdersPerBatch = maxOrdersPerBatch;
    }

    public int getMaxItemsPerOrder() {
        return maxItemsPerOrder;
    }

    public void setMaxItemsPerOrder(int maxItemsPerOrder) {
        this.maxItemsPerOrder = maxItemsPerOrder;
    }

    public int getMaxBootstrapPageSize() {
        return maxBootstrapPageSize;
    }

    public void setMaxBootstrapPageSize(int maxBootstrapPageSize) {
        this.maxBootstrapPageSize = maxBootstrapPageSize;
    }

    public int getMaxOfflineHoursDefault() {
        return maxOfflineHoursDefault;
    }

    public void setMaxOfflineHoursDefault(int maxOfflineHoursDefault) {
        this.maxOfflineHoursDefault = maxOfflineHoursDefault;
    }

    public boolean isAllowOfflineSync() {
        return allowOfflineSync;
    }

    public void setAllowOfflineSync(boolean allowOfflineSync) {
        this.allowOfflineSync = allowOfflineSync;
    }

    public boolean isRunTenantMigrationOnStartup() {
        return runTenantMigrationOnStartup;
    }

    public void setRunTenantMigrationOnStartup(boolean runTenantMigrationOnStartup) {
        this.runTenantMigrationOnStartup = runTenantMigrationOnStartup;
    }

    public Worker getWorker() {
        return worker;
    }

    public void setWorker(Worker worker) {
        this.worker = worker;
    }

    public static class Worker {
        private boolean enabled = false;
        private boolean processingEnabled = false;
        private boolean validationEnabled = false;
        private boolean postingEnabled = false;
        private int batchSize = 25;
        private long fixedDelayMs = 30000;
        private int stuckThresholdMinutes = 15;

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
    }
}
