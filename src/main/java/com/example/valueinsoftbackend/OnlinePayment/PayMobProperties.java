package com.example.valueinsoftbackend.OnlinePayment;

import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
@ConfigurationProperties(prefix = "vls.paymob")
@Slf4j
public class PayMobProperties {

    private String authToken;
    private int cardIntegrationId = 1989683;
    private int walletIntegrationId;
    private int cardIFrameId = 370887;
    private int walletIFrameId;

    @PostConstruct
    void initialize() {
        if (authToken == null || authToken.isBlank()) {
            log.warn("VLS_PAYMOB_AUTH_TOKEN is not configured. PayMob requests will fail until the secret is supplied.");
        }
    }

    public String getAuthToken() {
        return authToken;
    }

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }

    public int getCardIntegrationId() {
        return cardIntegrationId;
    }

    public void setCardIntegrationId(int cardIntegrationId) {
        this.cardIntegrationId = cardIntegrationId;
    }

    public int getWalletIntegrationId() {
        return walletIntegrationId;
    }

    public void setWalletIntegrationId(int walletIntegrationId) {
        this.walletIntegrationId = walletIntegrationId;
    }

    public int getCardIFrameId() {
        return cardIFrameId;
    }

    public void setCardIFrameId(int cardIFrameId) {
        this.cardIFrameId = cardIFrameId;
    }

    public int getWalletIFrameId() {
        return walletIFrameId;
    }

    public void setWalletIFrameId(int walletIFrameId) {
        this.walletIFrameId = walletIFrameId;
    }
}
