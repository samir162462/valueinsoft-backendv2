package com.example.valueinsoftbackend.notification.service;

import com.example.valueinsoftbackend.notification.model.NotificationDevice;
import com.example.valueinsoftbackend.notification.model.PushOutboxItem;
import com.example.valueinsoftbackend.notification.provider.ProviderErrorClassifier;
import com.example.valueinsoftbackend.notification.provider.PushProviderResponse;
import com.example.valueinsoftbackend.notification.repository.DbNotificationDeliveryAttempt;
import com.example.valueinsoftbackend.notification.repository.DbNotificationDevice;
import com.example.valueinsoftbackend.notification.repository.DbNotificationPushOutbox;
import com.example.valueinsoftbackend.notification.control.NotificationComponent;
import com.example.valueinsoftbackend.notification.scheduler.NotificationWorkSignal;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.Duration;

@Service
public class NotificationDeliveryResultService {
    private final DbNotificationPushOutbox outbox;
    private final DbNotificationDeliveryAttempt attempts;
    private final DbNotificationDevice devices;
    private final NotificationBackoffPolicy backoff;
    private final MeterRegistry meters;
    private final NotificationWorkSignal workSignal;

    @Autowired
    public NotificationDeliveryResultService(DbNotificationPushOutbox outbox,
                                             DbNotificationDeliveryAttempt attempts,
                                             DbNotificationDevice devices,
                                             NotificationBackoffPolicy backoff,
                                             MeterRegistry meters,
                                             ObjectProvider<NotificationWorkSignal> workSignalProvider) {
        this.outbox = outbox;
        this.attempts = attempts;
        this.devices = devices;
        this.backoff = backoff;
        this.meters = meters;
        this.workSignal = workSignalProvider.getIfAvailable();
    }

    NotificationDeliveryResultService(DbNotificationPushOutbox outbox,
                                      DbNotificationDeliveryAttempt attempts,
                                      DbNotificationDevice devices,
                                      NotificationBackoffPolicy backoff,
                                      MeterRegistry meters) {
        this.outbox = outbox;
        this.attempts = attempts;
        this.devices = devices;
        this.backoff = backoff;
        this.meters = meters;
        this.workSignal = null;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(PushOutboxItem item,
                         NotificationDevice device,
                         PushProviderResponse response,
                         ProviderErrorClassifier.Decision decision) {
        attempts.record(item, response, decision);
        if ("success".equals(decision.errorClass())) {
            outbox.markSent(item, decision.providerMessageId());
            devices.resetFailures(device.deviceId());
        } else {
            boolean dead = !decision.retryable() || item.attemptCount() >= item.maxAttempts();
            int delay = dead ? 0 : backoff.delaySeconds(
                    item.attemptCount(), decision.retryAfterSeconds());
            outbox.markFailed(
                    item, dead, decision.errorCode(),
                    response.transportError() == null
                            ? response.body()
                            : response.transportError().getClass().getSimpleName(),
                    delay);
            if (!dead && workSignal != null) {
                workSignal.signalAfterCommit(
                        NotificationComponent.DISPATCH, Duration.ofSeconds(delay));
            }
            applyDeviceAction(device, decision);
        }
        meters.counter("notification.push.result",
                "provider", item.provider(),
                "error_class", decision.errorClass()).increment();
        meters.timer("notification.push.latency", "provider", item.provider())
                .record(response.latency());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cancel(PushOutboxItem item, String reason) {
        outbox.cancel(item, reason);
        attempts.recordCancellation(item, reason);
        meters.counter("notification.push.binding_cancelled",
                "reason", reason).increment();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void requeue(PushOutboxItem item, String reason, int delaySeconds) {
        outbox.requeueWithoutAttempt(item, reason, delaySeconds);
        if (workSignal != null) {
            workSignal.signalAfterCommit(
                    NotificationComponent.DISPATCH, Duration.ofSeconds(delaySeconds));
        }
    }

    private void applyDeviceAction(NotificationDevice device,
                                   ProviderErrorClassifier.Decision decision) {
        if (decision.deviceAction() == ProviderErrorClassifier.DeviceAction.RESET
                || decision.deviceAction() == ProviderErrorClassifier.DeviceAction.NONE) {
            return;
        }
        if (decision.deviceAction() == ProviderErrorClassifier.DeviceAction.STALE
                && decision.invalidationAt() != null
                && device.lastRotatedAt() != null
                && !decision.invalidationAt().isAfter(device.lastRotatedAt())) {
            return;
        }
        OffsetDateTime invalidatedAt = decision.invalidationAt() == null
                ? OffsetDateTime.now() : decision.invalidationAt();
        devices.invalidate(
                device.deviceId(),
                decision.errorCode(),
                invalidatedAt,
                decision.deviceAction() == ProviderErrorClassifier.DeviceAction.REVOKE,
                decision.deviceAction() == ProviderErrorClassifier.DeviceAction.STALE,
                device.userId());
    }
}
