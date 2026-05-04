package com.example.valueinsoftbackend.pos.offline.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "valueinsoft.pos.offline.admin")
public class OfflinePosAdminProperties {

    private boolean postingEnabled = false;
    private int maxPostBatchSize = 50;

    public boolean isPostingEnabled() {
        return postingEnabled;
    }

    public void setPostingEnabled(boolean postingEnabled) {
        this.postingEnabled = postingEnabled;
    }

    public int getMaxPostBatchSize() {
        return maxPostBatchSize;
    }

    public void setMaxPostBatchSize(int maxPostBatchSize) {
        this.maxPostBatchSize = maxPostBatchSize;
    }
}
