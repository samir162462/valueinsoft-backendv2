package com.example.valueinsoftbackend.notification.stream;

import com.example.valueinsoftbackend.notification.config.NotificationProperties;
import com.example.valueinsoftbackend.notification.model.NotificationFeedItem;
import com.example.valueinsoftbackend.notification.repository.DbNotificationFeed;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Catches a reconnecting client up from its {@code Last-Event-ID} (NC-6.6, ADR-11).
 *
 * <p>Replay reads PostgreSQL, never Redis. That is deliberate: a Redis restart or outage
 * must not lose changes, and it is why the change log lives in the tenant schema rather
 * than in a Redis stream (§11.3). It also means replay works on the very first connection
 * after an app cold start, when Redis has never seen this client.
 */
@Service
public class FeedChangeReplayService {

    private final NotificationProperties properties;
    private final DbNotificationFeed feed;
    private final MeterRegistry meters;

    public FeedChangeReplayService(NotificationProperties properties,
                                   DbNotificationFeed feed,
                                   MeterRegistry meters) {
        this.properties = properties;
        this.feed = feed;
        this.meters = meters;
    }

    /** Either a bounded list of items to send, or an instruction to start over. */
    public sealed interface Outcome {
        record Replay(List<NotificationFeedItem> items) implements Outcome {
        }

        record Reset(String reason) implements Outcome {
        }
    }

    /**
     * @param lastEventId highest change sequence the client has fully processed; 0 or
     *                    negative means "first connection, send nothing and go live".
     */
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public Outcome catchUp(long companyId, int userId, long lastEventId) {
        if (lastEventId <= 0) {
            return new Outcome.Replay(List.of());
        }

        int limit = properties.getSse().getReplayLimit();

        // The client slept through the retention window. Silently skipping the gap would
        // leave it permanently out of date with no way to notice, so say so instead.
        long oldestRetained = feed.minRetainedChangeSequence(companyId, userId);
        if (oldestRetained > 0 && lastEventId < oldestRetained - 1) {
            meters.counter("notification.sse.reset", "reason", "replay_window_exceeded").increment();
            return new Outcome.Reset("replay_window_exceeded");
        }

        // Too far behind to stream incrementally: a full refetch is both cheaper and
        // more correct than dribbling out a truncated tail the client would misread as
        // complete.
        long pending = feed.pendingChangeCount(companyId, userId, lastEventId);
        if (pending > limit) {
            meters.counter("notification.sse.reset", "reason", "too_many_changes").increment();
            return new Outcome.Reset("too_many_changes");
        }

        List<NotificationFeedItem> items = feed.replaySince(companyId, userId, lastEventId, limit);
        meters.counter("notification.sse.replay").increment();
        return new Outcome.Replay(items);
    }
}
