package com.example.valueinsoftbackend.notification.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Static fallback for the runtime control plane (NOTIFICATION_CENTER_PLAN.md §16.5).
 *
 * <p>At runtime the authoritative control state lives in Redis so that a worker never has to
 * query the database in order to ask whether it may query the database. These values are used
 * only when Redis holds no state — a fresh or flushed Redis, or an instance starting while
 * Redis is unreachable. Falling back is not silent: it raises health to DEGRADED and sets
 * {@code notification.control.fallback_used}.
 *
 * <p>The defaults below are chosen so that a wiped Redis fails towards <em>delivering</em>
 * notifications rather than silently dropping them. Deployments run in resource-saver mode
 * should override {@code module} to false as well, so that both tiers agree.
 */
@Component
@ConfigurationProperties(prefix = "valueinsoft.notification.control")
@Getter
@Setter
public class NotificationControlProperties {

    /** Redis key namespace. Platform scope uses the reserved companyId 0 (§11.3). */
    private String keyPrefix = "notif:0:control";

    /** Pub/sub channel carrying control changes to every instance. */
    private String changeChannel = "notif:0:control:changed";

    /**
     * Backstop re-read of the full control snapshot from Redis, in case a pub/sub message
     * is missed. This is a Redis read, never a database read.
     */
    private long snapshotRefreshMs = 60_000L;

    /** Master runtime switch fallback. */
    private boolean module = true;

    /** Producer switch: when false, NotificationPublisher is a no-op and writes nothing. */
    private boolean publish = true;

    /** Per-worker fallbacks. Keys match ControlComponent names, e.g. DISPATCH, FANOUT. */
    private Map<String, Boolean> workers = new LinkedHashMap<>();

    /** Per-channel fallbacks: PUSH, IN_APP, SSE. */
    private Map<String, Boolean> channels = new LinkedHashMap<>();

    /** Per-provider fallbacks: FCM, APNS. */
    private Map<String, Boolean> providers = new LinkedHashMap<>();

    /** Per-API-surface fallbacks: FEED_READ, DEVICE_REGISTRATION, BROADCAST_CREATE. */
    private Map<String, Boolean> api = new LinkedHashMap<>();

    /** SUPPRESS | QUEUE | CANCEL — what happens to work generated while a switch is off (§16.3). */
    private String defaultSuppressionMode = "SUPPRESS";

    /** Interval at which timed disables ({@code disabled_until}) are re-checked. */
    private long expiryCheckMs = 60_000L;

    /**
     * Whether a queued push older than its TTL is delivered or discarded when a switch is
     * turned back on. Discarding is the default: nobody wants yesterday's low-stock alert.
     */
    private boolean dropStaleOnResume = true;

    public boolean workerEnabled(String component) {
        return module && workers.getOrDefault(component, Boolean.TRUE);
    }

    public boolean channelEnabled(String channel) {
        return module && channels.getOrDefault(channel, Boolean.TRUE);
    }

    public boolean providerEnabled(String provider) {
        return module && providers.getOrDefault(provider, Boolean.TRUE);
    }

    public boolean apiEnabled(String surface) {
        return module && api.getOrDefault(surface, Boolean.TRUE);
    }
}
