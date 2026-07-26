package com.example.valueinsoftbackend.notification.scheduler;

import com.example.valueinsoftbackend.notification.config.NotificationProperties;
import com.example.valueinsoftbackend.notification.control.ControlComponent;
import com.example.valueinsoftbackend.notification.control.NotificationComponent;
import com.example.valueinsoftbackend.notification.model.NotificationBroadcast.Batch;
import com.example.valueinsoftbackend.notification.model.NotificationBroadcast.SkipReason;
import com.example.valueinsoftbackend.notification.model.NotificationBroadcast.Target;
import com.example.valueinsoftbackend.notification.model.NotificationCatalogEntry;
import com.example.valueinsoftbackend.notification.model.NotificationEvent;
import com.example.valueinsoftbackend.notification.repository.DbNotificationBroadcast;
import com.example.valueinsoftbackend.notification.repository.DbNotificationBroadcastBatch;
import com.example.valueinsoftbackend.notification.repository.DbNotificationBroadcastTarget;
import com.example.valueinsoftbackend.notification.repository.BroadcastEventLocator;
import com.example.valueinsoftbackend.notification.repository.DbNotificationCatalog;
import com.example.valueinsoftbackend.notification.repository.NotificationAudienceResolver;
import com.example.valueinsoftbackend.notification.service.NotificationFanOutService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Materialises one broadcast batch per claim (NC-7.6, NC-7.8).
 *
 * <p>Two things make a retry safe. The batch is one transaction, so a failure materialises
 * nothing; and re-reading {@code pending} targets means the retry reaches exactly the people
 * the first attempt did not — anyone already done is no longer pending, and the tenant
 * lineage key would absorb them even if they were.
 *
 * <p>Eligibility is re-checked per target immediately before materialising. The snapshot
 * records <em>intent</em>, not entitlement: someone deactivated or stripped of a capability
 * since planning is marked {@code skipped} with a reason rather than quietly dropped, so the
 * operator can see "12,000 skipped because they left the company" instead of an unexplained
 * shortfall.
 */
@Component
@ConditionalOnProperty(name = "valueinsoft.notification.enabled", havingValue = "true")
@Slf4j
public class BroadcastMaterializationWorker implements NotificationWorkerTask {

    private final NotificationProperties properties;
    private final DbNotificationBroadcast broadcasts;
    private final DbNotificationBroadcastTarget targets;
    private final DbNotificationBroadcastBatch batches;
    private final DbNotificationCatalog catalog;
    private final NotificationAudienceResolver audience;
    private final NotificationFanOutService fanOut;
    private final BroadcastEventLocator events;
    private final MeterRegistry meters;
    private final String instanceId = UUID.randomUUID().toString();

    public BroadcastMaterializationWorker(NotificationProperties properties,
                                          DbNotificationBroadcast broadcasts,
                                          DbNotificationBroadcastTarget targets,
                                          DbNotificationBroadcastBatch batches,
                                          DbNotificationCatalog catalog,
                                          NotificationAudienceResolver audience,
                                          NotificationFanOutService fanOut,
                                          BroadcastEventLocator events,
                                          MeterRegistry meters) {
        this.properties = properties;
        this.broadcasts = broadcasts;
        this.targets = targets;
        this.batches = batches;
        this.catalog = catalog;
        this.audience = audience;
        this.fanOut = fanOut;
        this.events = events;
        this.meters = meters;
    }

    @Override
    public ControlComponent component() {
        return NotificationComponent.BROADCAST_MATERIALIZE;
    }

    @Override
    public Duration delay() {
        return Duration.ofMillis(properties.getBroadcast().getMaterializationPollDelayMs());
    }

    @Override
    public void runCycle() {
        batches.reclaimExpired();

        batches.claim(instanceId, properties.getBroadcast().getBatchLeaseSeconds())
                .ifPresent(this::processSafely);
    }

    private void processSafely(Batch batch) {
        try {
            process(batch);
            broadcasts.refreshCompletion(batch.broadcastId());
        } catch (RuntimeException ex) {
            log.error("Broadcast batch {} of broadcast {} failed: {}",
                    batch.batchNo(), batch.broadcastId(), ex.toString(), ex);
            batches.fail(batch.batchId(), ex.toString(), backoffSeconds(batch.attemptCount()));
            meters.counter("notification.broadcast.batch_failed").increment();
            if (batch.attemptCount() >= batch.maxAttempts()) {
                meters.counter("notification.broadcast.batch_dead").increment();
            }
        }
    }

    /** The whole batch is one transaction: it either materialises fully or not at all. */
    @Transactional
    void process(Batch batch) {
        NotificationEvent event = events.requireForBroadcast(batch.companyId(), batch.broadcastId());
        NotificationCatalogEntry type = catalog.requireActive(event.typeKey());

        int materialized = 0;
        int skipped = 0;
        int outboxCreated = 0;

        for (Target target : targets.pendingForBatch(batch.broadcastId(), batch.batchNo())) {
            SkipReason skip = eligibilityOf(target, type);
            if (skip != null) {
                targets.markSkipped(batch.broadcastId(), target.companyId(), target.userId(),
                        skip.code());
                skipped++;
                continue;
            }

            NotificationFanOutService.SingleUserResult result = fanOut.materializeSingleUser(
                    target.companyId(), target.userId(), localeFor(target), event, type);

            // A materialised target with zero outbox rows is normal and correct: the user has
            // no active device, or preferences suppressed the push. The feed row still exists,
            // so they will see it on next login — which is the whole point of a durable feed.
            targets.markMaterialized(batch.broadcastId(), target.companyId(), target.userId(),
                    result.recipientUuid(), result.outboxCreated());
            materialized++;
            outboxCreated += result.outboxCreated();
        }

        batches.complete(batch.batchId(), materialized, skipped, outboxCreated);
        broadcasts.addBatchResult(batch.broadcastId(), materialized, skipped, outboxCreated);

        meters.counter("notification.broadcast.batch_completed").increment();
        log.info("Broadcast {} batch {}: {} materialized, {} skipped, {} outbox rows",
                batch.broadcastId(), batch.batchNo(), materialized, skipped, outboxCreated);
    }

    /**
     * Re-check at materialisation time. Only the checks that are cheap and decisive live
     * here; preference-based suppression is deliberately *not* one of them, because
     * preferences suppress the push while still writing the feed row, and skipping the target
     * outright would lose the notification entirely.
     */
    private SkipReason eligibilityOf(Target target, NotificationCatalogEntry type) {
        if (!audience.canReceive(target.companyId(), target.userId(),
                target.branchId(), type.typeKey(), type.requiredCapability())) {
            return SkipReason.CAPABILITY_REVOKED;
        }
        return null;
    }

    private String localeFor(Target target) {
        // Falls back to English; the renderer applies the full locale chain
        // (requested → company default → en) and records which one it used.
        return "en";
    }

    private int backoffSeconds(int attempt) {
        int[] schedule = {30, 120, 600, 3_600, 21_600};
        return schedule[Math.min(Math.max(attempt - 1, 0), schedule.length - 1)];
    }
}
