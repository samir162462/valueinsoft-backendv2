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

    /** Stable name for logs and the {@code notification.worker.parked} gauge. */
    default String workerName() {
        return component().key();
    }
}
