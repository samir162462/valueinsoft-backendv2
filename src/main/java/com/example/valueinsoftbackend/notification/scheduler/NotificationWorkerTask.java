package com.example.valueinsoftbackend.notification.scheduler;

import com.example.valueinsoftbackend.notification.control.ControlComponent;

import java.time.Duration;

/**
 * A notification worker that the module's private scheduler may run.
 *
 * <p>Implementations are registered with {@link NotificationTaskScheduler}, which schedules
 * them only while their {@link #component()} is enabled and cancels them outright when it is
 * not. A parked worker's {@link #runCycle()} is never entered, so it issues no statements and
 * borrows no connection (invariant B-16).
 *
 * <p>Implementations must not swallow their own scheduling concerns: no {@code @Scheduled}
 * annotation, no self-managed timer, and no control check inside {@link #runCycle()} — the
 * scheduler owns both.
 */
public interface NotificationWorkerTask {

    /** The switch that governs this worker. */
    ControlComponent component();

    /** Delay between the end of one cycle and the start of the next. */
    Duration delay();

    /** Delay before the first cycle after the worker is armed. */
    default Duration initialDelay() {
        return delay();
    }

    /** One unit of work. Exceptions are logged by the scheduler and do not unschedule the task. */
    void runCycle();

    /**
     * Queue consumers opt into wake/drain/park operation. Periodic maintenance tasks keep their
     * ordinary fixed-delay schedule outside the operating quiet window.
     */
    default boolean eventDriven() {
        return false;
    }

    /**
     * Runs one event-driven drain cycle.
     *
     * @return true when the worker filled its bounded batch and should remain armed; false when
     *         it observed an empty/partially drained queue and may park until the next signal.
     */
    default boolean runEventDrivenCycle() {
        runCycle();
        return true;
    }

    /** Stable name for logs and the {@code notification.worker.parked} gauge. */
    default String workerName() {
        return component().key();
    }
}
