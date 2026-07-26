package com.example.valueinsoftbackend.notification.stream;

import com.example.valueinsoftbackend.notification.config.NotificationProperties;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-instance registry of live SSE emitters (NC-6.4).
 *
 * <p>This is deliberately node-local. Cross-instance delivery is Redis pub/sub's job
 * ({@link RedisNotificationRelay}); every instance holds only the emitters it accepted, and
 * a message fanned out on Redis is delivered by whichever instances happen to hold that
 * user's connections.
 *
 * <p>Two caps, for two different failure modes: per-user, so one runaway client cannot
 * open connections without bound; and per-instance, so a stampede degrades into polling
 * rather than exhausting the servlet container's async capacity.
 */
@Component
@ConditionalOnProperty(name = "valueinsoft.notification.enabled", havingValue = "true")
@Slf4j
public class SseConnectionRegistry {

    /** Emitters for one (company, user), oldest first so the cap evicts the oldest. */
    private final Map<String, Deque<Connection>> byUser = new ConcurrentHashMap<>();
    private final Map<String, List<Connection>> bySession = new ConcurrentHashMap<>();
    private final AtomicInteger total = new AtomicInteger();

    private final NotificationProperties properties;

    public SseConnectionRegistry(NotificationProperties properties, MeterRegistry meters) {
        this.properties = properties;
        Gauge.builder("notification.sse.connections", total, AtomicInteger::get)
                .description("Live SSE emitters held by this instance")
                .register(meters);
    }

    public record Connection(SseEmitter emitter, long companyId, int userId, String sessionId) {
    }

    private static String userKey(long companyId, int userId) {
        return companyId + ":" + userId;
    }

    private static String sessionKey(long companyId, String sessionId) {
        return companyId + ":" + sessionId;
    }

    /** @return false when the per-instance cap is reached; the caller should answer 503. */
    public boolean register(Connection connection) {
        if (total.get() >= properties.getSse().getMaxConnectionsPerInstance()) {
            log.warn("SSE instance cap reached ({}); refusing connection for company {} user {}",
                    properties.getSse().getMaxConnectionsPerInstance(),
                    connection.companyId(), connection.userId());
            return false;
        }

        String key = userKey(connection.companyId(), connection.userId());
        Deque<Connection> queue = byUser.computeIfAbsent(key, k -> new ArrayDeque<>());

        List<Connection> evicted = new ArrayList<>();
        synchronized (queue) {
            queue.addLast(connection);
            while (queue.size() > properties.getSse().getMaxConnectionsPerUser()) {
                Connection oldest = queue.pollFirst();
                if (oldest != null) evicted.add(oldest);
            }
        }

        total.incrementAndGet();
        bySession.computeIfAbsent(sessionKey(connection.companyId(), connection.sessionId()),
                k -> java.util.Collections.synchronizedList(new ArrayList<>())).add(connection);

        // Evicting the oldest rather than refusing the newest means a user who reloaded a
        // stale tab keeps working; the abandoned tab is the one that loses its stream.
        for (Connection old : evicted) {
            send(old, NotificationStreamEvent.SESSION_INVALIDATED, null,
                    new NotificationStreamEvent.SessionInvalidatedPayload("connection_limit"));
            complete(old);
        }
        return true;
    }

    public void unregister(Connection connection) {
        String key = userKey(connection.companyId(), connection.userId());
        Deque<Connection> queue = byUser.get(key);
        if (queue != null) {
            synchronized (queue) {
                if (queue.remove(connection)) {
                    total.decrementAndGet();
                }
                if (queue.isEmpty()) byUser.remove(key, queue);
            }
        }
        List<Connection> sessionConnections =
                bySession.get(sessionKey(connection.companyId(), connection.sessionId()));
        if (sessionConnections != null) sessionConnections.remove(connection);
    }

    public List<Connection> connectionsFor(long companyId, int userId) {
        Deque<Connection> queue = byUser.get(userKey(companyId, userId));
        if (queue == null) return List.of();
        synchronized (queue) {
            return List.copyOf(queue);
        }
    }

    /**
     * Logout and company switch (NC-6.7). Closes within milliseconds rather than at the next
     * heartbeat — a stream left open after logout would keep feeding a signed-out session.
     */
    public void closeSession(long companyId, String sessionId, String reason) {
        List<Connection> connections =
                bySession.remove(sessionKey(companyId, sessionId));
        if (connections == null) return;
        for (Connection connection : List.copyOf(connections)) {
            send(connection, NotificationStreamEvent.SESSION_INVALIDATED, null,
                    new NotificationStreamEvent.SessionInvalidatedPayload(reason));
            complete(connection);
        }
    }

    /** Best-effort send. A failed write means the client is gone; drop the emitter. */
    public void send(Connection connection, String event, Long id, Object payload) {
        try {
            SseEmitter.SseEventBuilder builder = SseEmitter.event().name(event).data(payload);
            if (id != null) builder = builder.id(Long.toString(id));
            connection.emitter().send(builder);
        } catch (IOException | IllegalStateException ex) {
            complete(connection);
        }
    }

    public void ping(Connection connection) {
        try {
            // A comment frame, not an event: it keeps proxies from idling the connection out
            // without waking client-side event handlers.
            connection.emitter().send(SseEmitter.event().comment("ping"));
        } catch (IOException | IllegalStateException ex) {
            complete(connection);
        }
    }

    public void complete(Connection connection) {
        try {
            connection.emitter().complete();
        } catch (RuntimeException ignored) {
            // Already completed or the container has torn it down.
        }
        unregister(connection);
    }

    public int liveConnections() {
        return total.get();
    }

    /**
     * Close every stream on shutdown so clients reconnect to a healthy instance immediately
     * rather than hanging until their read timeout.
     */
    @PreDestroy
    public void shutdown() {
        byUser.values().forEach(queue -> {
            synchronized (queue) {
                List.copyOf(queue).forEach(this::complete);
            }
        });
        byUser.clear();
        bySession.clear();
        total.set(0);
    }
}
