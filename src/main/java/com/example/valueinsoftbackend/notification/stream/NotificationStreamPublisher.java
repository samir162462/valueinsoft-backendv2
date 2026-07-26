package com.example.valueinsoftbackend.notification.stream;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Publishes live-stream hints, always <strong>after commit</strong> (§C-16).
 *
 * <p>Publishing inside the transaction is the classic bug in this shape: the client is woken,
 * calls back, and reads a row the publishing transaction has not committed yet — so it sees
 * nothing and concludes the notification does not exist. Deferring to {@code afterCommit}
 * means a woken client always finds the row.
 *
 * <p>Callers pass the change sequence they just allocated. If there is no active transaction
 * — a path that should not exist, but might after a refactor — the hint is published
 * immediately rather than dropped.
 */
@Service
public class NotificationStreamPublisher {

    private final ObjectProvider<RedisNotificationRelay> relayProvider;

    @org.springframework.beans.factory.annotation.Autowired
    public NotificationStreamPublisher(ObjectProvider<RedisNotificationRelay> relayProvider) {
        this.relayProvider = relayProvider;
    }

    /**
     * A publisher that never publishes.
     *
     * <p>Used by integration tests that construct services directly, and by any deployment
     * without Redis. This is safe precisely because the stream is a latency optimisation and
     * never a correctness mechanism: the feed row is committed either way, and a client
     * recovers everything through {@code Last-Event-ID} replay from PostgreSQL (ADR-11).
     */
    public NotificationStreamPublisher() {
        this.relayProvider = null;
    }

    private RedisNotificationRelay relay() {
        return relayProvider == null ? null : relayProvider.getIfAvailable();
    }

    public void publishAfterCommit(long companyId, int userId, long changeSequence, long recipientId) {
        RedisNotificationRelay relay = relay();
        if (relay == null) {
            // Notifications are disabled, or Redis is not configured. The feed row is still
            // committed and the client will see it on its next poll or reconnect.
            return;
        }

        RedisNotificationRelay.FeedChangeHint hint =
                new RedisNotificationRelay.FeedChangeHint(companyId, userId, changeSequence, recipientId);

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            relay.publishFeedChange(hint);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                relay.publishFeedChange(hint);
            }
        });
    }

    /** Logout and company switch: close the session's streams on every instance (NC-6.7). */
    public void killSession(long companyId, String sessionId, String reason) {
        RedisNotificationRelay relay = relay();
        if (relay != null) {
            relay.publishSessionKill(companyId, sessionId, reason);
        }
    }
}
