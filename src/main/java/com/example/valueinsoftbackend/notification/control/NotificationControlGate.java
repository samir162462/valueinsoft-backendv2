package com.example.valueinsoftbackend.notification.control;

/**
 * Read side of the control plane (NOTIFICATION_CENTER_PLAN.md §16).
 *
 * <p>Implementations must answer {@link #isEnabled} <strong>without touching the database</strong>.
 * A control check that queries PostgreSQL would defeat the entire purpose: every poll cycle
 * would query the database in order to ask whether it may query the database. Phase 0 answers
 * from static configuration; Phase 2 (NC-2.26) replaces it with a Redis-backed snapshot kept
 * current by pub/sub, which is still not a database read.
 */
public interface NotificationControlGate {

    /** Whether the component may run right now. Must be cheap and database-free. */
    boolean isEnabled(ControlComponent component);

    /** SUPPRESS, QUEUE or CANCEL for work encountered while the component is disabled. */
    default String suppressionMode(ControlComponent component) {
        return "SUPPRESS";
    }

    /**
     * Registers a callback invoked whenever the control snapshot changes, so the scheduler can
     * park and re-arm tasks event-driven rather than on a timer. With everything parked and
     * re-arming event-driven, the module holds no timers and issues no statements at all.
     */
    void addChangeListener(Runnable listener);

    /** Where the current snapshot came from — {@code static}, {@code redis} or {@code fallback}. */
    String source();
}
