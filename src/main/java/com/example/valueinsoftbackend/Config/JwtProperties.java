package com.example.valueinsoftbackend.Config;

import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Base64;
import java.util.UUID;

@Component
@ConfigurationProperties(prefix = "vls.jwt")
@Slf4j
public class JwtProperties {

    private String secret;
    private long expirationMs = 259200000L;

    @PostConstruct
    void initialize() {
        if (secret == null || secret.isBlank()) {
            secret = Base64.getEncoder().encodeToString((UUID.randomUUID().toString() + UUID.randomUUID()).getBytes());
            log.warn("VLS_JWT_SECRET is not configured. Generated an ephemeral JWT secret for this process.");
        }
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    public void setExpirationMs(long expirationMs) {
        this.expirationMs = expirationMs;
    }
}
