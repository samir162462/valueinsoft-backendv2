package com.example.valueinsoftbackend.whatsapp.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
@ConfigurationProperties(prefix = "vls.whatsapp")
@Data
@Slf4j
public class WhatsAppProperties {

    private String encryptionKey;
    private String defaultGraphApiVersion = "v21.0";
    private int requestTimeoutMs = 30000;
    private int maxMessageBodyLength = 4096;
    private int maxRetryAttempts = 3;
    private long retryInitialDelayMs = 1000;

    @PostConstruct
    void initialize() {
        if (encryptionKey == null || encryptionKey.isBlank() || encryptionKey.length() < 32) {
            log.warn("VLS_WHATSAPP_ENCRYPTION_KEY is not properly configured. " +
                     "Encryption key must be at least 32 characters long. " +
                     "WhatsApp access tokens may not be encrypted securely.");
        }
    }
}
