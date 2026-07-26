package com.example.valueinsoftbackend.notification.control;

/**
 * The notification module's switchable components (NOTIFICATION_CENTER_PLAN.md §16.2).
 *
 * <p>Ordering matters for the admin screen only. Keys are persisted and must not change.
 */
public enum NotificationComponent implements ControlComponent {

    /** Master runtime switch. Off implies everything below is off. */
    MODULE("module", "Notification module"),

    /** Producer switch: off means NotificationPublisher writes nothing at all. */
    PUBLISH("module", "Publish events"),

    // ── Workers ──────────────────────────────────────────────────────────────
    FANOUT("worker", "Fan-out worker"),
    DISPATCH("worker", "Dispatch worker"),
    BROADCAST_PLANNING("worker", "Broadcast planning"),
    BROADCAST_MATERIALIZE("worker", "Broadcast materialisation"),
    STUCK_CLAIM_REAPER("worker", "Stuck-claim reaper"),
    RETENTION("worker", "Retention purge"),
    DEVICE_REAPER("worker", "Device reaper"),
    PARTITION_MAINTENANCE("worker", "Partition maintenance"),
    CONSISTENCY("worker", "Consistency checks"),

    // ── Channels ─────────────────────────────────────────────────────────────
    PUSH("channel", "Push delivery"),
    IN_APP("channel", "In-app feed rows"),
    SSE("channel", "Live stream"),

    // ── Providers ────────────────────────────────────────────────────────────
    FCM("provider", "Firebase Cloud Messaging"),
    APNS("provider", "Apple Push Notification service"),

    // ── API surfaces ─────────────────────────────────────────────────────────
    FEED_READ("api", "Feed and summary endpoints"),
    DEVICE_REGISTRATION("api", "Device registration"),
    BROADCAST_CREATE("api", "Broadcast creation"),

    /**
     * Re-enables timed disables when {@code disabled_until} elapses. Never switchable —
     * something has to be able to turn the system back on (invariant B-17).
     */
    CONTROL_EXPIRY("worker", "Control expiry", false);

    private final String scope;
    private final String displayName;
    private final boolean switchable;

    NotificationComponent(String scope, String displayName) {
        this(scope, displayName, true);
    }

    NotificationComponent(String scope, String displayName, boolean switchable) {
        this.scope = scope;
        this.displayName = displayName;
        this.switchable = switchable;
    }

    @Override
    public String key() {
        return name();
    }

    @Override
    public String scope() {
        return scope;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    @Override
    public boolean switchable() {
        return switchable;
    }
}
