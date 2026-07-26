package com.example.valueinsoftbackend.notification.service;

import com.example.valueinsoftbackend.notification.model.NotificationCatalogEntry;
import com.example.valueinsoftbackend.notification.model.NotificationEvent;
import com.example.valueinsoftbackend.notification.model.RenderedNotification;
import com.example.valueinsoftbackend.notification.repository.DbNotificationFeedChange;
import com.example.valueinsoftbackend.notification.repository.DbNotificationRecipient;
import com.example.valueinsoftbackend.notification.repository.DbNotificationRecipientEvent;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class NotificationAggregationService {
    private final DbNotificationRecipient recipients;
    private final DbNotificationRecipientEvent lineage;
    private final DbNotificationFeedChange changes;

    public NotificationAggregationService(DbNotificationRecipient recipients,
                                          DbNotificationRecipientEvent lineage,
                                          DbNotificationFeedChange changes) {
        this.recipients = recipients;
        this.lineage = lineage;
        this.changes = changes;
    }

    /**
     * Executes the aggregation protocol in its documented order. The caller owns the
     * transaction and retries the whole transaction on a unique-open-group race.
     */
    public Outcome apply(long companyId, int userId, NotificationEvent event,
                         NotificationCatalogEntry catalog, RenderedNotification rendered) {
        // Fast path for ordinary retries. The database insert below remains the authoritative
        // gate for races where two workers both miss this pre-check.
        if (lineage.exists(companyId, event.eventId(), userId)) {
            return new Outcome(0, null, 0, false, true);
        }
        // STEP 1: find and lock the open group.
        Optional<DbNotificationRecipient.OpenRecipient> open =
                recipients.lockOpen(companyId, userId, rendered.groupKey());

        // STEP 2: decide the target; an expired group is explicitly closed.
        DbNotificationRecipient.OpenRecipient aggregateTarget = null;
        long targetId;
        int lineageSequence;
        UUID recipientUuid;
        String previousState = null;
        boolean created;
        boolean withinWindow = open.isPresent()
                && catalog.aggregationWindowSeconds() > 0
                && open.get().lastEventAt().isAfter(
                        event.createdAt().minusSeconds(catalog.aggregationWindowSeconds()));
        if (withinWindow) {
            aggregateTarget = open.get();
            targetId = aggregateTarget.recipientId();
            lineageSequence = aggregateTarget.aggregateCount() + 1;
            recipientUuid = aggregateTarget.recipientUuid();
            previousState = aggregateTarget.state();
            created = false;
        } else {
            if (open.isPresent()) {
                long closeSequence = recipients.closeGroup(companyId, open.get().recipientId());
                changes.insert(companyId, closeSequence, userId, open.get().recipientId(),
                        "group_closed", event.eventId());
            }
            long createSequence = changes.nextSequence(companyId);
            DbNotificationRecipient.NewRecipient inserted =
                    recipients.insert(companyId, userId, event, catalog, rendered, createSequence);
            targetId = inserted.recipientId();
            recipientUuid = inserted.recipientUuid();
            lineageSequence = 1;
            created = true;
        }

        // STEP 3/4: lineage is the exactly-once gate; the exception forces full rollback.
        if (!lineage.insert(companyId, event.eventId(), userId, targetId, lineageSequence)) {
            throw new AlreadyAppliedException(event.eventId(), userId);
        }

        // STEP 5: a new row already holds event #1 because chk_nr_aggregate requires >= 1.
        long changeSequence;
        String changeType;
        if (created) {
            changeSequence = recipients.findChangeSequence(companyId, targetId);
            changeType = "created";
        } else {
            changeSequence = recipients.aggregate(
                    companyId, aggregateTarget, event, rendered);
            changeType = "aggregated";
        }

        // STEP 6: durable change log.
        changes.insert(companyId, changeSequence, userId, targetId, changeType, event.eventId());

        // STEP 7: state audit.
        recipients.audit(companyId, targetId, userId, catalog.category(), previousState,
                "unseen", "system");
        return new Outcome(targetId, recipientUuid, lineageSequence, created, false);
    }

    public record Outcome(
            long recipientId,
            UUID recipientUuid,
            int aggregateCount,
            boolean created,
            boolean alreadyApplied) {}

    public static class AlreadyAppliedException extends RuntimeException {
        public AlreadyAppliedException(long eventId, int userId) {
            super("Event " + eventId + " was already applied to user " + userId);
        }
    }
}
