package com.example.valueinsoftbackend.notification.control;

/**
 * A switchable unit of work in the control plane (NOTIFICATION_CENTER_PLAN.md §16.2).
 *
 * <p>This is deliberately an interface rather than a notification-specific enum. The stated
 * goal of the control plane is to let PostgreSQL suspend while idle, and no single module can
 * achieve that alone — this codebase has roughly a dozen scheduled database consumers
 * (OfflinePosWorker, BillingSchedulerJobs, GlobalFxRateScheduler, the company-insight jobs,
 * and others). Keeping the contract generic means those jobs can adopt the same admin screen
 * and the same scheduling mechanism later without a redesign. See §14.10.
 */
public interface ControlComponent {

    /**
     * Stable identifier, unique within {@link #scope()}. Persisted in
     * {@code notification_control_switch.component_key} and used as a Redis hash field,
     * so it must never change once released.
     */
    String key();

    /**
     * One of: {@code module}, {@code worker}, {@code channel}, {@code provider}, {@code api},
     * {@code tenant}, {@code category}, {@code type}, {@code branch}. Matches
     * {@code notification_control_switch.scope}.
     */
    String scope();

    /** Human-readable label for the admin screen. */
    default String displayName() {
        return key();
    }

    /**
     * Whether this component may be switched off at all. The control-expiry mechanism must
     * never be switchable, or a timed disable could never re-enable itself (invariant B-17).
     */
    default boolean switchable() {
        return true;
    }
}
