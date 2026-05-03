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
}
