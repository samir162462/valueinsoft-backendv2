package com.example.valueinsoftbackend.notification.scheduler;

import com.example.valueinsoftbackend.notification.config.NotificationProperties;
import com.example.valueinsoftbackend.notification.config.NotificationResourceSaverProperties;
import com.example.valueinsoftbackend.notification.control.NotificationComponent;
import com.example.valueinsoftbackend.notification.control.NotificationControlGate;
import com.example.valueinsoftbackend.notification.control.NotificationOperatingWindowService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The notification module's private scheduler (NOTIFICATION_CENTER_PLAN.md §16.4).
 *
 * <p><strong>Why this exists instead of {@code @Scheduled}.</strong> This application has no
 * global {@code @EnableScheduling}. That is deliberate: it is why every {@code @Scheduled}
 * method in the codebase — {@code OfflinePosWorker}, {@code BillingSchedulerJobs},
 * {@code GlobalFxRateScheduler}, the company-insight jobs — is currently inert, and why
 * PostgreSQL is free to suspend while the application is idle. Turning on
 * {@code @EnableScheduling} to run notification workers would activate every one of those
 * dormant jobs at once. So the module brings its own scheduler and registers only its own
 * tasks.
 *
 * <p><strong>Parked means unscheduled, not short-circuited.</strong> When a worker's control
 * component is disabled its {@link ScheduledFuture} is cancelled outright. No thread wakes, no
 * {@code JdbcTemplate} method is entered, no connection is borrowed. Re-arming is driven by the
 * control/work callbacks rather than a PostgreSQL polling supervisor. With every worker
 * parked, the only optional timer is the in-memory operating-window boundary timer; it does
 * not enter worker code or borrow a database connection.
 */
@Component
@ConditionalOnProperty(name = "valueinsoft.notification.enabled", havingValue = "true")
@Slf4j
public class NotificationTaskScheduler implements SmartLifecycle {

    private final NotificationProperties properties;
    private final NotificationResourceSaverProperties resourceSaverProperties;
    private final NotificationControlGate controlGate;
    private final NotificationOperatingWindowService operatingWindow;
    private final NotificationWorkSignal workSignal;
    private final MeterRegistry meterRegistry;
    private final List<NotificationWorkerTask> tasks;

    private final Map<String, ScheduledFuture<?>> scheduled = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> parkedGauges = new ConcurrentHashMap<>();
    private final Map<String, Long> handledWorkVersions = new ConcurrentHashMap<>();
    private final Set<String> discoveryCompleted = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean running = new AtomicBoolean(false);

    private ThreadPoolTaskScheduler scheduler;
    private ScheduledFuture<?> operatingTransition;
    private Instant operatingTransitionAt;

    /**
     * {@code tasks} is injected as an {@link ObjectProvider} rather than a {@code List} because
     * Phase 0 ships no worker implementations at all, and Spring treats an empty collection
     * as an unsatisfied dependency when injected directly.
     */
    public NotificationTaskScheduler(NotificationProperties properties,
                                     NotificationResourceSaverProperties resourceSaverProperties,
                                     NotificationControlGate controlGate,
                                     NotificationOperatingWindowService operatingWindow,
                                     NotificationWorkSignal workSignal,
                                     MeterRegistry meterRegistry,
                                     ObjectProvider<NotificationWorkerTask> taskProvider) {
        this.properties = properties;
        this.resourceSaverProperties = resourceSaverProperties;
        this.controlGate = controlGate;
        this.operatingWindow = operatingWindow;
        this.workSignal = workSignal;
        this.meterRegistry = meterRegistry;
        this.tasks = taskProvider.orderedStream().toList();
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        NotificationProperties.Scheduler cfg = properties.getScheduler();
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(cfg.getPoolSize());
        scheduler.setThreadNamePrefix(cfg.getThreadNamePrefix());
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(cfg.getShutdownAwaitSeconds());
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.initialize();

        tasks.forEach(this::registerGauge);

        // Event-driven re-arm: no polling supervisor unless explicitly enabled.
        controlGate.addChangeListener(this::reconcile);
        operatingWindow.addChangeListener(this::reconcile);
        workSignal.addListener(this::workAvailable);

        if (cfg.isSupervisorEnabled()) {
            scheduler.scheduleWithFixedDelay(
                    this::reconcile,
                    Instant.now().plusMillis(cfg.getSupervisorIntervalMs()),
                    Duration.ofMillis(cfg.getSupervisorIntervalMs()));
            log.info("Notification scheduler supervisor enabled at {}ms", cfg.getSupervisorIntervalMs());
        }

        reconcile();

        log.info("Notification scheduler started: {} registered task(s), control source={}",
                tasks.size(), controlGate.source());
    }

    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        scheduled.values().forEach(future -> future.cancel(false));
        scheduled.clear();
        if (operatingTransition != null) {
            operatingTransition.cancel(false);
            operatingTransition = null;
            operatingTransitionAt = null;
        }
        if (scheduler != null) {
            scheduler.shutdown();
        }
        log.info("Notification scheduler stopped");
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Start late and stop early, so workers never run before the rest of the context is ready
     * and are quiesced before datasources close.
     */
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 100;
    }

    // ── Arming and parking ───────────────────────────────────────────────────

    /**
     * Brings the scheduled set in line with the control plane. Idempotent and safe to call
     * from a control-change callback on any thread.
     */
    public synchronized void reconcile() {
        if (!running.get()) {
            return;
        }
        boolean quiet = operatingWindow.isQuietNow();
        for (NotificationWorkerTask task : tasks) {
            boolean shouldRun = controlGate.isEnabled(task.component()) && !quiet;
            if (shouldRun && resourceSaverProperties.isEventDriven() && task.eventDriven()) {
                NotificationComponent component = notificationComponent(task);
                shouldRun = !discoveryCompleted.contains(task.workerName())
                        || workSignal.version(component)
                        > handledWorkVersions.getOrDefault(task.workerName(), 0L);
            }
            boolean isArmed = scheduled.containsKey(task.workerName());

            if (shouldRun && !isArmed) {
                arm(task);
            } else if (!shouldRun && isArmed) {
                park(task);
            }
        }
        scheduleOperatingTransition();
    }

    private void arm(NotificationWorkerTask task) {
        Duration initialDelay = resourceSaverProperties.isEventDriven() && task.eventDriven()
                ? Duration.ZERO : task.initialDelay();
        ScheduledFuture<?> future = scheduler.scheduleWithFixedDelay(
                () -> runGuarded(task),
                Instant.now().plus(initialDelay),
                task.delay());
        scheduled.put(task.workerName(), future);
        parkedGauge(task.workerName()).set(0);
        log.info("Notification worker armed: {} (delay={})", task.workerName(), task.delay());
    }

    private void park(NotificationWorkerTask task) {
        ScheduledFuture<?> future = scheduled.remove(task.workerName());
        if (future != null) {
            // false: let an in-flight cycle finish its transaction rather than interrupting it.
            // Switches take effect at cycle boundaries, never mid-transaction (§16.10).
            future.cancel(false);
        }
        parkedGauge(task.workerName()).set(1);
        log.info("Notification worker parked: {} — no further executions scheduled", task.workerName());
    }

    void runGuarded(NotificationWorkerTask task) {
        // A callback may already have been dequeued when reconcile() cancels its future.
        // Re-check the in-memory/Redis control snapshot at the cycle boundary so that race
        // cannot enter worker repository code after the switch has been turned off.
        if (!controlGate.isEnabled(task.component()) || operatingWindow.isQuietNow()) {
            return;
        }
        try {
            if (!resourceSaverProperties.isEventDriven() || !task.eventDriven()) {
                task.runCycle();
                return;
            }

            NotificationComponent component = notificationComponent(task);
            long startedVersion = workSignal.version(component);
            boolean keepArmed = task.runEventDrivenCycle();
            discoveryCompleted.add(task.workerName());
            if (!keepArmed) {
                handledWorkVersions.put(task.workerName(), startedVersion);
                if (workSignal.version(component) <= startedVersion) {
                    park(task);
                }
            }
        } catch (Exception ex) {
            // A failing cycle must not unschedule the task; the next cycle retries.
            log.error("Notification worker {} cycle failed: {}", task.workerName(), ex.toString(), ex);
        }
    }

    private synchronized void workAvailable(NotificationComponent component, long version) {
        if (!running.get() || operatingWindow.isQuietNow()) {
            return;
        }
        for (NotificationWorkerTask task : tasks) {
            if (task.eventDriven()
                    && task.component().key().equals(component.key())
                    && controlGate.isEnabled(task.component())
                    && version > handledWorkVersions.getOrDefault(task.workerName(), 0L)
                    && !scheduled.containsKey(task.workerName())) {
                arm(task);
            }
        }
    }

    private synchronized void scheduleOperatingTransition() {
        Instant next = operatingWindow.nextTransitionAfter(Instant.now());
        if (java.util.Objects.equals(next, operatingTransitionAt)) {
            return;
        }
        if (operatingTransition != null) {
            operatingTransition.cancel(false);
        }
        operatingTransitionAt = next;
        operatingTransition = next == null ? null : scheduler.schedule(() -> {
            synchronized (NotificationTaskScheduler.this) {
                operatingTransition = null;
                operatingTransitionAt = null;
            }
            reconcile();
        }, next.plusMillis(100));
    }

    private static NotificationComponent notificationComponent(NotificationWorkerTask task) {
        if (task.component() instanceof NotificationComponent component) {
            return component;
        }
        throw new IllegalStateException(
                "Event-driven worker must use NotificationComponent: " + task.workerName());
    }

    // ── Metrics ──────────────────────────────────────────────────────────────

    private void registerGauge(NotificationWorkerTask task) {
        AtomicInteger holder = parkedGauge(task.workerName());
        Gauge.builder("notification.worker.parked", holder, AtomicInteger::get)
                .description("1 when the worker is unscheduled by the control plane, 0 when armed")
                .tag("worker", task.workerName())
                .register(meterRegistry);
    }

    private AtomicInteger parkedGauge(String workerName) {
        return parkedGauges.computeIfAbsent(workerName, name -> new AtomicInteger(1));
    }

    /** Exposed for tests: the set of workers currently holding a scheduled future. */
    public java.util.Set<String> armedWorkers() {
        return java.util.Set.copyOf(scheduled.keySet());
    }
}
