package com.example.valueinsoftbackend.notification.service;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Redis token buckets for notification rate limits (NC-5.6, §6.12).
 *
 * <p><strong>Keys are tenant-scoped.</strong> Every key begins {@code notif:{companyId}:};
 * platform scope uses the reserved literal {@code notif:0:}. Without the prefix two tenants
 * sharing a numeric user id would share a bucket, and one tenant's traffic would throttle
 * another's (§11.3).
 *
 * <p><strong>Fail-open versus fail-closed is deliberate, per scope.</strong> When Redis is
 * unavailable the limiter cannot count, and the safe answer differs by what is being
 * limited:
 *
 * <ul>
 *   <li><em>Fail open</em> for publish, feed reads and device registration. A notification
 *       system exists to deliver notifications; refusing them because a cache is down turns
 *       a Redis blip into lost cash-variance alerts.</li>
 *   <li><em>Fail closed</em> for broadcast creation, dead-letter retry and resend. These are
 *       destructive, admin-triggered and rare. Refusing one until Redis returns costs an
 *       operator thirty seconds; allowing an unbounded one costs every user on the platform.</li>
 * </ul>
 */
@Service
@Slf4j
public class NotificationRateLimiter {

    /** Which way a limit fails when Redis cannot answer. */
    public enum FailureMode {
        OPEN, CLOSED
    }

    public record Limit(String scope, int permits, Duration window, FailureMode failureMode) {
    }

    // Defaults from §6.12. Producer limits can be overridden per type by the catalog's
    // producer_rate_limit_per_min column, which is why publishPerType takes an explicit value.
    public static final Limit USER_INBOUND = new Limit("user", 60, Duration.ofMinutes(1), FailureMode.OPEN);
    public static final Limit DEVICE_REGISTRATION = new Limit("devreg", 10, Duration.ofHours(1), FailureMode.OPEN);
    public static final Limit SSE_TICKET = new Limit("ssetkt", 30, Duration.ofMinutes(5), FailureMode.OPEN);
    public static final Limit BROADCAST_CREATE = new Limit("broadcast", 5, Duration.ofHours(1), FailureMode.CLOSED);
    public static final Limit ADMIN_RETRY = new Limit("retry", 1, Duration.ofMinutes(1), FailureMode.CLOSED);
    public static final Limit ADMIN_RESEND = new Limit("resend", 1, Duration.ofMinutes(1), FailureMode.CLOSED);

    private final ObjectProvider<StringRedisTemplate> redisProvider;
    private final MeterRegistry meters;

    public NotificationRateLimiter(ObjectProvider<StringRedisTemplate> redisProvider,
                                   MeterRegistry meters) {
        this.redisProvider = redisProvider;
        this.meters = meters;
    }

    private static String key(long companyId, String scope, String identifier) {
        return "notif:" + companyId + ":rl:" + scope + ":" + identifier;
    }

    /**
     * Fixed-window counter: {@code INCR} then {@code EXPIRE} on first hit.
     *
     * <p>A fixed window can admit up to 2× the limit across a boundary. That is accepted
     * here — these limits exist to stop runaway producers and accidental admin loops, not to
     * meter a paid API — and a sliding window would need a sorted set and several round
     * trips per check on the fan-out hot path.
     */
    public boolean tryAcquire(long companyId, Limit limit, String identifier) {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null) {
            return allowOnFailure(limit, "redis_absent");
        }

        String redisKey = key(companyId, limit.scope(), identifier);
        try {
            Long count = redis.opsForValue().increment(redisKey);
            if (count == null) {
                return allowOnFailure(limit, "null_reply");
            }
            if (count == 1L) {
                redis.expire(redisKey, limit.window());
            }
            if (count > limit.permits()) {
                meters.counter("notification.ratelimit.rejected", "scope", limit.scope()).increment();
                return false;
            }
            return true;
        } catch (RuntimeException ex) {
            return allowOnFailure(limit, ex.getClass().getSimpleName());
        }
    }

    /** Producer limit for one type; the catalog may override the default permits. */
    public boolean tryAcquireProducer(long companyId, String typeKey, Integer permitsPerMinute) {
        int permits = permitsPerMinute == null ? 600 : permitsPerMinute;
        return tryAcquire(companyId,
                new Limit("type", permits, Duration.ofMinutes(1), FailureMode.OPEN),
                typeKey);
    }

    private boolean allowOnFailure(Limit limit, String reason) {
        boolean allowed = limit.failureMode() == FailureMode.OPEN;
        meters.counter("notification.ratelimit.degraded",
                "scope", limit.scope(),
                "outcome", allowed ? "open" : "closed").increment();
        log.warn("Rate limiter unavailable for scope={} ({}); failing {}",
                limit.scope(), reason, allowed ? "open" : "closed");
        return allowed;
    }
}
