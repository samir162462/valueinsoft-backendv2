package com.example.valueinsoftbackend.notification.stream;

/**
 * SSE event names (NOTIFICATION_CENTER_PLAN.md §7.4).
 *
 * <p>The SSE {@code id} field carries {@code change_sequence}, never {@code recipient_id}
 * (ADR-11). Clients echo the highest fully processed value back as {@code Last-Event-ID}.
 */
public final class NotificationStreamEvent {

    /** A feed item was created or changed. Data is a {@code NotificationFeedItem}. */
    public static final String NOTIFICATION = "notification";

    /** Badge counts changed. Data is a {@code NotificationSummary}. */
    public static final String SUMMARY = "summary";

    /**
     * The client's {@code Last-Event-ID} is older than the retained change log, or more
     * changes are pending than the replay bound allows. The client must refetch
     * {@code /feed} and {@code /summary} and resume from the returned {@code changeSequence}.
     */
    public static final String RESET = "reset";

    /** The session was invalidated — logout, company switch, or a connection-cap eviction. */
    public static final String SESSION_INVALIDATED = "session-invalidated";

    /** Keep-alive. Emitted every 25s to stay under common 30/60s proxy idle timeouts. */
    public static final String PING = "ping";

    private NotificationStreamEvent() {
    }

    public record ResetPayload(String reason, long changeSequence) {
    }

    public record SessionInvalidatedPayload(String reason) {
    }
}
