package com.example.valueinsoftbackend.Config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Base64;
import java.util.UUID;

@Component
@ConfigurationProperties(prefix = "vls.jwt")
public class JwtProperties {

    private static final Logger log = LoggerFactory.getLogger(JwtProperties.class);

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
