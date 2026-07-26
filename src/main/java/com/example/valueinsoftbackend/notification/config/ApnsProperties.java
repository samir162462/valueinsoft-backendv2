package com.example.valueinsoftbackend.notification.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Direct APNs HTTP/2 provider configuration (NOTIFICATION_CENTER_PLAN.md §6.4).
 *
 * <p>Credentials follow the existing ValueINSoft convention: environment variables in
 * production, git-ignored {@code config/application-dev.local.properties} locally. The
 * {@code .p8} key is supplied base64-encoded in {@code VLS_NOTIFICATION_APNS_PRIVATE_KEY_B64}
 * so it can travel as a single environment variable, exactly as
 * {@code vls.whatsapp.encryption-key} does today.
 *
 * <p>There is deliberately no {@code topic} property. The APNs topic is the target app's
 * bundle identifier, which differs per build variant, so it is read from
 * {@code notification_device.app_bundle_id} per message. A static topic here would silently
 * send every push to the production bundle.
 */
@Component
@ConfigurationProperties(prefix = "valueinsoft.notification.apns")
@Getter
@Setter
public class ApnsProperties {

    private boolean enabled = false;

    /** Apple Developer Team ID (10 characters). */
    private String teamId = "";

    /** APNs Auth Key ID (10 characters), matching the .p8 file. */
    private String keyId = "";

    /** Base64-encoded contents of the AuthKey_XXXXXXXXXX.p8 file. Never logged. */
    private String privateKeyBase64 = "";

    private String productionHost = "https://api.push.apple.com";
    private String sandboxHost = "https://api.sandbox.push.apple.com";

    /**
     * ES256 provider-token lifetime before regeneration. Apple rejects tokens older than
     * 60 minutes and rejects regeneration more often than every 20 minutes, so 50 sits
     * safely inside both bounds.
     */
    private int jwtRefreshMinutes = 50;

    /** Minimum gap between forced refreshes triggered by 403 ExpiredProviderToken. */
    private int jwtForcedRefreshFloorMinutes = 20;

    /**
     * Self-imposed concurrent stream limit per connection. Apple advertises roughly 1000;
     * a lower ceiling keeps latency predictable and the blast radius bounded.
     */
    private int maxConcurrentStreams = 200;

    private int connectTimeoutSeconds = 5;
    private int requestTimeoutSeconds = 10;

    /** Consecutive transport failures before the circuit breaker opens. */
    private int circuitBreakerFailureThreshold = 10;
    private int circuitBreakerOpenSeconds = 60;

    /** Seconds to wait for in-flight sends during shutdown before closing the client. */
    private int shutdownDrainSeconds = 30;

    /** Daily synthetic probe that verifies the key still authenticates (§6.4). */
    private boolean credentialProbeEnabled = true;
    private String credentialProbeTopic = "";

    public boolean isConfigured() {
        return !teamId.isBlank() && !keyId.isBlank() && !privateKeyBase64.isBlank();
    }

    public String hostFor(String apnsEnvironment) {
        return "production".equals(apnsEnvironment) ? productionHost : sandboxHost;
    }
}
