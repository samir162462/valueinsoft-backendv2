package com.example.valueinsoftbackend.notification.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Cross-instance SSE fan-out (NC-6.5, NC-6.7).
 *
 * <p>A notification is materialised on whichever instance ran the fan-out worker, but the
 * user's stream is held by whichever instance accepted their connection — usually a
 * different one. Redis pub/sub bridges the two.
 *
 * <p><strong>Redis is not on the correctness path.</strong> If it is down, live delivery
 * stops and nothing else does: the feed row is already committed, the client notices the
 * missing pings, reconnects, and {@code Last-Event-ID} replays every missed change from
 * PostgreSQL (§11.3). That is why this class only ever carries a *hint* — the change
 * sequence and the recipient id — and never the notification content itself.
 */
@Component
@ConditionalOnProperty(name = "valueinsoft.notification.enabled", havingValue = "true")
@Slf4j
public class RedisNotificationRelay {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisNotificationRelay(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    /** Channel pattern the listener container subscribes to: {@code notif:*:user:*}. */
    public static final String USER_CHANNEL_PATTERN = "notif:*:user:*";
    public static final String SESSION_KILL_PATTERN = "notif:*:session:*:kill";

    public static String userChannel(long companyId, int userId) {
        return "notif:" + companyId + ":user:" + userId;
    }

    public static String sessionKillChannel(long companyId, String sessionId) {
        return "notif:" + companyId + ":session:" + sessionId + ":kill";
    }

    /** Hint that a user's feed changed. Content is deliberately absent. */
    public record FeedChangeHint(long companyId, int userId, long changeSequence, long recipientId) {
    }

    public record SessionKill(long companyId, String sessionId, String reason) {
    }

    /**
     * Published {@code afterCommit}, never inside the transaction. Publishing early would
     * wake a client that then reads a row its own transaction cannot see yet.
     */
    public void publishFeedChange(FeedChangeHint hint) {
        try {
            redis.convertAndSend(
                    userChannel(hint.companyId(), hint.userId()),
                    objectMapper.writeValueAsString(hint));
        } catch (Exception ex) {
            // Losing the hint costs live latency, not data: the client's next reconnect
            // replays from the database. Not worth failing the caller.
            log.debug("SSE relay publish failed: {}", ex.toString());
        }
    }

    public void publishSessionKill(long companyId, String sessionId, String reason) {
        try {
            redis.convertAndSend(
                    sessionKillChannel(companyId, sessionId),
                    objectMapper.writeValueAsString(new SessionKill(companyId, sessionId, reason)));
        } catch (Exception ex) {
            log.debug("SSE session-kill publish failed: {}", ex.toString());
        }
    }

    /** Parses an inbound hint; returns null on anything malformed rather than throwing. */
    public FeedChangeHint parseHint(String payload) {
        try {
            return objectMapper.readValue(payload, FeedChangeHint.class);
        } catch (Exception ex) {
            log.debug("Unparseable SSE hint: {}", ex.toString());
            return null;
        }
    }

    public SessionKill parseSessionKill(String payload) {
        try {
            return objectMapper.readValue(payload, SessionKill.class);
        } catch (Exception ex) {
            log.debug("Unparseable SSE session kill: {}", ex.toString());
            return null;
        }
    }
}
