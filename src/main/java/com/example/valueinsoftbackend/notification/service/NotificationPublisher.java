package com.example.valueinsoftbackend.notification.service;

import com.example.valueinsoftbackend.notification.config.NotificationProperties;
import com.example.valueinsoftbackend.notification.control.NotificationComponent;
import com.example.valueinsoftbackend.notification.control.NotificationControlGate;
import com.example.valueinsoftbackend.notification.model.NotificationPublishResult;
import com.example.valueinsoftbackend.notification.model.NotificationRequest;
import com.example.valueinsoftbackend.notification.repository.DbNotificationEvent;
import com.example.valueinsoftbackend.notification.repository.DbNotificationFanOutJob;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationPublisher {
    private final NotificationProperties properties;
    private final ObjectProvider<NotificationControlGate> gateProvider;
    private final NotificationIdempotencyService idempotency;
    private final DbNotificationEvent events;
    private final DbNotificationFanOutJob jobs;

    public NotificationPublisher(NotificationProperties properties,
                                 ObjectProvider<NotificationControlGate> gateProvider,
                                 NotificationIdempotencyService idempotency,
                                 DbNotificationEvent events,
                                 DbNotificationFanOutJob jobs) {
        this.properties = properties;
        this.gateProvider = gateProvider;
        this.idempotency = idempotency;
        this.events = events;
        this.jobs = jobs;
    }

    @Transactional
    public NotificationPublishResult publish(NotificationRequest request) {
        if (!properties.isEnabled()) {
            return NotificationPublishResult.suppressedResult();
        }
        NotificationControlGate gate = gateProvider.getIfAvailable();
        if (gate != null && !gate.isEnabled(NotificationComponent.PUBLISH)) {
            return NotificationPublishResult.suppressedResult();
        }

        byte[] fingerprint = idempotency.fingerprint(request);
        var inserted = events.insert(request, fingerprint);
        if (inserted.isPresent()) {
            long eventId = inserted.get();
            jobs.insert(request.companyId(), eventId, request.broadcastId(),
                    properties.getFanOut().getMaxAttempts());
            return new NotificationPublishResult(eventId, true, false);
        }

        DbNotificationEvent.ExistingEvent existing =
                events.requireExisting(request.companyId(), request.idempotencyKey());
        idempotency.assertSame(fingerprint, existing.fingerprint(), request.idempotencyKey());
        return new NotificationPublishResult(existing.eventId(), false, false);
    }
}
