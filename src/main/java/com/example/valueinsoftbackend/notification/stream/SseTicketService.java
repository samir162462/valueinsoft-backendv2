package com.example.valueinsoftbackend.notification.stream;

import com.example.valueinsoftbackend.notification.config.NotificationProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

/**
 * Single-use SSE connection tickets (NC-6.1, ADR-7).
 *
 * <p>The browser's native {@code EventSource} cannot set request headers, so the stream
 * cannot carry a bearer token the normal way. The main JWT must never go in a URL — it
 * lands in access logs, proxy logs, browser history and {@code Referer} headers, and it is
 * long-lived. Instead the client exchanges its JWT for a ticket that is worth almost
 * nothing: 30 seconds, one use, bound to a single user, company and session.
 *
 * <p>Mobile does not use tickets at all — {@code expo/fetch} supports headers, so React
 * Native sends the bearer token like every other call (§8.5).
 */
@Service
@Slf4j
public class SseTicketService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final NotificationProperties properties;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public SseTicketService(NotificationProperties properties,
                            StringRedisTemplate redis,
                            ObjectMapper objectMapper) {
        this.properties = properties;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public record Ticket(String value, int expiresInSeconds) {
    }

    public record TicketClaims(long companyId, int userId, String username, String sessionId) {
    }

    /**
     * Tickets are stored under a hash of their value, not the value itself. Redis keys turn
     * up in {@code MONITOR} output, slow-log entries and memory dumps; hashing means seeing
     * the key does not hand over a usable credential.
     */
    private String keyFor(long companyId, String ticket) {
        return "notif:" + companyId + ":sse:ticket:" + sha256(ticket);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    public Ticket issue(TicketClaims claims) {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String ticket = URL_ENCODER.encodeToString(raw);

        int ttl = properties.getSse().getTicketTtlSeconds();
        try {
            redis.opsForValue().set(
                    keyFor(claims.companyId(), ticket),
                    objectMapper.writeValueAsString(claims),
                    Duration.ofSeconds(ttl));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to issue SSE ticket", ex);
        }
        return new Ticket(ticket, ttl);
    }

    /**
     * Redeems atomically with {@code GETDEL}, so a replayed ticket finds nothing and is
     * rejected. Without the atomicity two concurrent connections could both redeem the same
     * ticket, which is exactly the replay this design exists to prevent.
     *
     * <p>The caller must still re-check that the session is live and that the user holds
     * {@code notification.feed.read.self} for the company in the claims — a ticket proves
     * only that a valid JWT existed 30 seconds ago.
     */
    public Optional<TicketClaims> redeem(long companyId, String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return Optional.empty();
        }
        try {
            String payload = redis.opsForValue().getAndDelete(keyFor(companyId, ticket));
            if (payload == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(payload, TicketClaims.class));
        } catch (Exception ex) {
            log.warn("SSE ticket redemption failed: {}", ex.toString());
            return Optional.empty();
        }
    }

    /**
     * Tickets are not indexed by session, so logout does not hunt them down — they expire
     * within 30 seconds, and scanning for them would cost more than waiting. What actually
     * ends a session is closing its emitters ({@link SseConnectionRegistry#closeSession}),
     * plus the session-validity re-check the stream performs on redemption: a ticket
     * redeemed inside the residual window still fails that check.
     */
    public int ticketTtlSeconds() {
        return properties.getSse().getTicketTtlSeconds();
    }
}
