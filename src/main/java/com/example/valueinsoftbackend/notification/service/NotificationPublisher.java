package com.example.valueinsoftbackend.notification.service;

import com.example.valueinsoftbackend.notification.config.NotificationProperties;
import com.example.valueinsoftbackend.notification.control.NotificationComponent;
import com.example.valueinsoftbackend.notification.control.NotificationControlGate;
import com.example.valueinsoftbackend.notification.model.NotificationPublishResult;
import com.example.valueinsoftbackend.notification.model.NotificationRequest;
import com.example.valueinsoftbackend.notification.repository.DbNotificationEvent;
import com.example.valueinsoftbackend.notification.repository.DbNotificationFanOutJob;
import com.example.valueinsoftbackend.notification.scheduler.NotificationWorkSignal;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class NotificationPublisher {
    private final NotificationProperties properties;
    private final ObjectProvider<NotificationControlGate> gateProvider;
    private final NotificationIdempotencyService idempotency;
    private final DbNotificationEvent events;
    private final DbNotificationFanOutJob jobs;
    private NotificationWorkSignal workSignal;
    private TransactionTemplate transactions;

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

    /**
     * Configure the transaction boundary programmatically so both disable gates run before
     * Spring borrows a database connection. An {@code @Transactional} annotation on this
     * method would start the transaction before the first line executes.
     */
    @Autowired
    void configureTransactionManager(PlatformTransactionManager transactionManager) {
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Autowired(required = false)
    void configureWorkSignal(NotificationWorkSignal workSignal) {
        this.workSignal = workSignal;
    }

    public NotificationPublishResult publish(NotificationRequest request) {
        if (!properties.isEnabled()) {
            return NotificationPublishResult.suppressedResult();
        }
        NotificationControlGate gate = gateProvider.getIfAvailable();
        if (gate != null && !gate.isEnabled(NotificationComponent.PUBLISH)) {
            return NotificationPublishResult.suppressedResult();
        }

        if (transactions == null) {
            // Directly constructed unit/integration fixtures are not Spring-managed and
            // therefore have no transaction manager. Production always takes the branch below.
            NotificationPublishResult result = persist(request);
            signalFanOut(result);
            return result;
        }
        NotificationPublishResult result = transactions.execute(status -> persist(request));
        if (result == null) {
            throw new IllegalStateException("Notification publish transaction returned no result");
        }
        signalFanOut(result);
        return result;
    }

    private NotificationPublishResult persist(NotificationRequest request) {
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

    private void signalFanOut(NotificationPublishResult result) {
        if (!result.created()) {
            return;
        }
        if (workSignal != null) {
            workSignal.signal(NotificationComponent.FANOUT);
        }
    }
}
