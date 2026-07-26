package com.example.valueinsoftbackend.notification.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/**
 * FCM HTTP v1 provider configuration (NOTIFICATION_CENTER_PLAN.md §6.3).
 *
 * <p>The service-account JSON is supplied base64-encoded in a single environment variable,
 * following the existing secret convention. An OAuth2 access token is derived from it and
 * cached in memory, refreshed at {@link #tokenRefreshRatio} of its lifetime.
 */
@Component
@ConfigurationProperties(prefix = "valueinsoft.notification.fcm")
@Getter
@Setter
public class FcmProperties {

    private boolean enabled = false;

    /** Firebase project id. Used to build the send endpoint. */
    private String projectId = "";

    /** Base64-encoded service-account JSON with the Firebase Messaging role. Never logged. */
    private String serviceAccountJsonBase64 = "";

    /** {projectId} is substituted at runtime. */
    private String sendEndpoint = "https://fcm.googleapis.com/v1/projects/{projectId}/messages:send";

    private String tokenEndpoint = "https://oauth2.googleapis.com/token";
    private String scope = "https://www.googleapis.com/auth/firebase.messaging";

    /** Refresh the OAuth2 token once this fraction of its lifetime has elapsed. */
    private double tokenRefreshRatio = 0.80d;

    private int connectTimeoutSeconds = 5;
    private int requestTimeoutSeconds = 10;

    private int circuitBreakerFailureThreshold = 10;
    private int circuitBreakerOpenSeconds = 60;

    /** Daily check that the service-account key is more than this many days from expiry. */
    private boolean credentialProbeEnabled = true;
    private int credentialExpiryWarningDays = 30;
    private OffsetDateTime credentialExpiresAt;

    public boolean isConfigured() {
        return !projectId.isBlank() && !serviceAccountJsonBase64.isBlank();
    }

    public String resolvedSendEndpoint() {
        return sendEndpoint.replace("{projectId}", projectId);
    }
}
