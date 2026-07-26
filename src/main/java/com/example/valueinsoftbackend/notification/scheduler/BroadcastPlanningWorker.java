package com.example.valueinsoftbackend.notification.scheduler;

import com.example.valueinsoftbackend.notification.config.NotificationProperties;
import com.example.valueinsoftbackend.notification.control.ControlComponent;
import com.example.valueinsoftbackend.notification.control.NotificationComponent;
import com.example.valueinsoftbackend.notification.model.AudienceMember;
import com.example.valueinsoftbackend.notification.model.NotificationBroadcast.Row;
import com.example.valueinsoftbackend.notification.model.NotificationCatalogEntry;
import com.example.valueinsoftbackend.notification.model.NotificationRequest;
import com.example.valueinsoftbackend.notification.repository.DbNotificationBroadcast;
import com.example.valueinsoftbackend.notification.repository.DbNotificationBroadcastBatch;
import com.example.valueinsoftbackend.notification.repository.DbNotificationBroadcastTarget;
import com.example.valueinsoftbackend.notification.repository.DbNotificationBroadcastTarget.NewTarget;
import com.example.valueinsoftbackend.notification.repository.DbNotificationCatalog;
import com.example.valueinsoftbackend.notification.repository.DbNotificationEvent;
import com.example.valueinsoftbackend.notification.repository.NotificationAudienceResolver;
import com.example.valueinsoftbackend.notification.service.NotificationIdempotencyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Resolves a broadcast's audience once and freezes it (NC-7.4, NC-7.5, ADR-12).
 *
 * <p>Planning is a single transaction per broadcast: targets, batches, the tenant event and
 * the parent status all commit together, so a partially planned broadcast never exists. If
 * the worker dies mid-planning the lease expires, the broadcast returns to {@code scheduled},
 * and re-planning reproduces an identical target set because inserts are keyed on
 * {@code (broadcast_id, company_id, user_id)}.
 */
@Component
@ConditionalOnProperty(name = "valueinsoft.notification.enabled", havingValue = "true")
@Slf4j
public class BroadcastPlanningWorker implements NotificationWorkerTask {

    private final NotificationProperties properties;
    private final DbNotificationBroadcast broadcasts;
    private final DbNotificationBroadcastTarget targets;
    private final DbNotificationBroadcastBatch batches;
    private final DbNotificationCatalog catalog;
    private final NotificationAudienceResolver audience;
    private final DbNotificationEvent events;
    private final NotificationIdempotencyService idempotency;
    private final String instanceId = UUID.randomUUID().toString();

    public BroadcastPlanningWorker(NotificationProperties properties,
                                   DbNotificationBroadcast broadcasts,
                                   DbNotificationBroadcastTarget targets,
                                   DbNotificationBroadcastBatch batches,
                                   DbNotificationCatalog catalog,
                                   NotificationAudienceResolver audience,
                                   DbNotificationEvent events,
                                   NotificationIdempotencyService idempotency) {
        this.properties = properties;
        this.broadcasts = broadcasts;
        this.targets = targets;
        this.batches = batches;
        this.catalog = catalog;
        this.audience = audience;
        this.events = events;
        this.idempotency = idempotency;
    }

    @Override
    public ControlComponent component() {
        return NotificationComponent.BROADCAST_PLANNING;
    }

    @Override
    public Duration delay() {
        return Duration.ofMillis(properties.getBroadcast().getPlanningPollDelayMs());
    }

    @Override
    public void runCycle() {
        broadcasts.reclaimExpiredPlanning();

        // One broadcast per cycle. Planning can insert tens of thousands of rows, and taking
        // several at once would hold a long transaction while another instance sits idle.
        broadcasts.claimForPlanning(instanceId,
                        properties.getBroadcast().getPlanningLeaseSeconds())
                .ifPresent(this::planSafely);
    }

    private void planSafely(Row broadcast) {
        try {
            plan(broadcast);
        } catch (RuntimeException ex) {
            log.error("Broadcast planning failed for {}: {}",
                    broadcast.broadcastUuid(), ex.toString(), ex);
            broadcasts.markFailed(broadcast.broadcastId(), ex.toString());
        }
    }

    @Transactional
    void plan(Row broadcast) {
        if (broadcast.companyId() == null) {
            throw new IllegalStateException("Platform-scope broadcasts are not supported yet");
        }
        long companyId = broadcast.companyId();
        NotificationCatalogEntry type = catalog.requireActive(broadcast.typeKey());
        int batchSize = properties.getBroadcast().getBatchSize();

        // The tenant event every target will be materialised against. Without it
        // BroadcastEventLocator has nothing to find and every batch fails, so this is the
        // first thing planning writes.
        //
        // Deliberate divergence from §2.3: planning writes the event but **not** a
        // notification_fanout_job. The broadcast batch workers are the fan-out mechanism
        // here; adding a job would have the generic NotificationFanOutWorker race them over
        // the same audience. The lineage key would keep the result correct, but the duplicate
        // work would leave targets sitting 'pending' while their recipient rows already
        // existed — counters that contradict reality are worse than none. §2.3 should be
        // corrected to match.
        createBroadcastEvent(broadcast, companyId);

        // Page the audience rather than loading it whole: a 50,000-user tenant would
        // otherwise materialise the entire list in memory before writing anything.
        List<NewTarget> pending = new ArrayList<>();
        int cursor = 0;
        int totalTargets = 0;
        int batchNo = 1;
        int inBatch = 0;

        while (true) {
            List<AudienceMember> page = audience.fetchBatch(
                    companyId, broadcast.branchId(), broadcast.typeKey(),
                    type.requiredCapability(), cursor, batchSize);
            if (page.isEmpty()) {
                break;
            }
            for (AudienceMember member : page) {
                pending.add(new NewTarget(member.userId(), broadcast.branchId(), batchNo));
                totalTargets++;
                inBatch++;
                if (inBatch >= batchSize) {
                    batches.create(broadcast.broadcastId(), companyId, batchNo, inBatch,
                            properties.getBroadcast().getBatchMaxAttempts());
                    batchNo++;
                    inBatch = 0;
                }
                cursor = member.userId();
            }
            if (pending.size() >= properties.getBroadcast().getTargetInsertChunkSize()) {
                targets.insertAll(broadcast.broadcastId(), companyId, pending,
                        properties.getBroadcast().getTargetInsertChunkSize());
                pending.clear();
            }
            if (page.size() < batchSize) {
                break;
            }
        }

        if (!pending.isEmpty()) {
            targets.insertAll(broadcast.broadcastId(), companyId, pending,
                    properties.getBroadcast().getTargetInsertChunkSize());
        }
        if (inBatch > 0) {
            batches.create(broadcast.broadcastId(), companyId, batchNo, inBatch,
                    properties.getBroadcast().getBatchMaxAttempts());
        }

        // targeted_count comes from the rows actually written, never from the loop counter.
        // If the two ever disagree — a conflict absorbed an insert, say — the row count is
        // the truth, because that is what the materialisation worker will iterate.
        int actualTargets = targets.countTargets(broadcast.broadcastId());
        int actualBatches = targets.maxBatchNo(broadcast.broadcastId());

        broadcasts.markPlanned(broadcast.broadcastId(), actualTargets, actualBatches);

        log.info("Broadcast {} planned: {} targets across {} batches (counted {} while paging)",
                broadcast.broadcastUuid(), actualTargets, actualBatches, totalTargets);
    }

    /**
     * Creates the tenant event, idempotently.
     *
     * <p>The idempotency key is derived from the broadcast uuid and company, so re-planning
     * after a crashed lease reuses the same event rather than creating a second one — which
     * would give every target a fresh lineage row and send the broadcast twice.
     */
    private void createBroadcastEvent(Row broadcast, long companyId) {
        String idempotencyKey = "broadcast:" + broadcast.broadcastUuid() + ":" + companyId;

        NotificationRequest request = new NotificationRequest(
                companyId,
                broadcast.typeKey(),
                idempotencyKey,
                broadcast.branchId(),
                broadcast.createdByUserId(),
                "broadcast",
                broadcast.broadcastId(),
                broadcast.params(),
                broadcast.priority(),
                // No group key: collapsing a broadcast into someone's existing feed item
                // would hide it behind an unrelated aggregate.
                null,
                "broadcast",
                broadcast.broadcastId(),
                "broadcast:" + broadcast.broadcastUuid());

        events.insert(request, idempotency.fingerprint(request));
    }
}
