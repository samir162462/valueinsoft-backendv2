package com.example.valueinsoftbackend.notification.scheduler;

import com.example.valueinsoftbackend.notification.config.NotificationProperties;
import com.example.valueinsoftbackend.notification.control.ControlComponent;
import com.example.valueinsoftbackend.notification.control.NotificationComponent;
import com.example.valueinsoftbackend.notification.control.NotificationControlGate;
import com.example.valueinsoftbackend.notification.repository.DbNotificationPushOutbox;
import com.example.valueinsoftbackend.notification.service.NotificationDispatchService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "valueinsoft.notification.enabled", havingValue = "true")
public class NotificationDispatchWorker implements NotificationWorkerTask {
    private final NotificationProperties properties;
    private final DbNotificationPushOutbox outbox;
    private final NotificationDispatchService dispatch;
    private final TransactionTemplate transactions;
    private final NotificationControlGate controls;
    private final NotificationPartitionJob partitionMaintenance;
    private volatile boolean dispatchEnabled;
    private volatile boolean preflightRequired = true;
    private volatile Instant resumedAt = Instant.EPOCH;
    private long rateWindowStartedNanos = System.nanoTime();
    private int claimedInRateWindow;
    private final String workerId = ManagementFactory.getRuntimeMXBean().getName()
            + ":dispatch:" + UUID.randomUUID();

    public NotificationDispatchWorker(NotificationProperties properties,
                                      DbNotificationPushOutbox outbox,
                                      NotificationDispatchService dispatch,
                                      PlatformTransactionManager transactionManager,
                                      NotificationControlGate controls,
                                      NotificationPartitionJob partitionMaintenance) {
        this.properties = properties;
        this.outbox = outbox;
        this.dispatch = dispatch;
        this.transactions = new TransactionTemplate(transactionManager);
        this.controls = controls;
        this.partitionMaintenance = partitionMaintenance;
        this.dispatchEnabled = controls.isEnabled(NotificationComponent.DISPATCH);
        controls.addChangeListener(this::controlChanged);
    }

    @Override
    public ControlComponent component() {
        return NotificationComponent.DISPATCH;
    }

    @Override
    public Duration delay() {
        return Duration.ofMillis(properties.getDispatch().getPollDelayMs());
    }

    @Override
    public synchronized void runCycle() {
        if (preflightRequired) {
            partitionMaintenance.runCycle();
            preflightRequired = false;
        }
        if (!outbox.currentPartitionExists()) {
            throw new IllegalStateException(
                    "Current notification push-outbox partition is missing");
        }
        int rate = Instant.now().isBefore(resumedAt.plus(Duration.ofMinutes(5)))
                ? properties.getDispatch().getResumeMaxPerSecond()
                : properties.getDispatch().getMaxPerSecond();
        refreshRateWindow();
        int allowance = Math.max(0, rate - claimedInRateWindow);
        int batchSize = Math.min(
                properties.getDispatch().getClaimBatchSize(),
                allowance);
        if (batchSize == 0) {
            return;
        }
        var claimed = transactions.execute(status -> outbox.claim(
                batchSize, workerId, properties.getDispatch().getLeaseSeconds()));
        if (claimed != null) {
            claimedInRateWindow += claimed.size();
            claimed.forEach(dispatch::dispatch);
        }
    }

    private void controlChanged() {
        boolean enabled = controls.isEnabled(NotificationComponent.DISPATCH);
        if (enabled && !dispatchEnabled) {
            resumedAt = Instant.now();
            preflightRequired = true;
        }
        dispatchEnabled = enabled;
    }

    private void refreshRateWindow() {
        long now = System.nanoTime();
        if (now - rateWindowStartedNanos >= Duration.ofSeconds(1).toNanos()) {
            rateWindowStartedNanos = now;
            claimedInRateWindow = 0;
        }
    }
}
