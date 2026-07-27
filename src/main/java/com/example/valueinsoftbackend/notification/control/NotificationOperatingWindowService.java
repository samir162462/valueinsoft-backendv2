package com.example.valueinsoftbackend.notification.control;

import com.example.valueinsoftbackend.notification.config.NotificationResourceSaverProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Platform-wide, Redis-backed quiet window for notification database workers.
 *
 * <p>Reads are served from an in-memory snapshot. Redis pub/sub refreshes the snapshot when an
 * administrator changes it; no PostgreSQL lookup is involved in deciding whether a worker may
 * run.
 */
@Service
@ConditionalOnProperty(name = "valueinsoft.notification.enabled", havingValue = "true")
public class NotificationOperatingWindowService {
    private final NotificationResourceSaverProperties properties;
    private final ObjectProvider<StringRedisTemplate> redisProvider;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    private volatile Window snapshot;
    private volatile String source = "fallback";

    public NotificationOperatingWindowService(
            NotificationResourceSaverProperties properties,
            ObjectProvider<StringRedisTemplate> redisProvider) {
        this.properties = properties;
        this.redisProvider = redisProvider;
        this.snapshot = fallback();
    }

    @PostConstruct
    public void initialize() {
        refresh();
    }

    public WindowView view() {
        Window current = snapshot;
        Instant now = Instant.now();
        return new WindowView(
                current.enabled(),
                current.quietStart().toString(),
                current.quietEnd().toString(),
                current.timezone().getId(),
                isQuietAt(current, now),
                nextTransitionAfter(current, now),
                source);
    }

    public boolean isQuietNow() {
        return isQuietAt(snapshot, Instant.now());
    }

    boolean isQuietAt(Instant instant) {
        return isQuietAt(snapshot, instant);
    }

    public Instant nextTransitionAfter(Instant now) {
        return nextTransitionAfter(snapshot, now);
    }

    public void addChangeListener(Runnable listener) {
        listeners.add(listener);
    }

    public synchronized WindowView update(
            boolean enabled, String quietStart, String quietEnd, String timezone) {
        Window requested = validate(enabled, quietStart, quietEnd, timezone);
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null) {
            throw new IllegalStateException(
                    "Redis is required to persist the notification operating window");
        }
        Map<String, String> values = Map.of(
                "enabled", Boolean.toString(requested.enabled()),
                "quietStart", requested.quietStart().toString(),
                "quietEnd", requested.quietEnd().toString(),
                "timezone", requested.timezone().getId());
        try {
            redis.opsForHash().putAll(properties.getOperatingWindowKey(), values);
            redis.convertAndSend(properties.getOperatingWindowChannel(), "changed");
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "Redis is unavailable; the notification operating window was not changed",
                    exception);
        }
        replace(requested, "redis");
        return view();
    }

    public synchronized void refresh() {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null) {
            replace(fallback(), "fallback");
            return;
        }
        try {
            Map<Object, Object> values =
                    redis.opsForHash().entries(properties.getOperatingWindowKey());
            if (values.isEmpty()) {
                replace(fallback(), "fallback");
                return;
            }
            replace(validate(
                    Boolean.parseBoolean(string(values, "enabled")),
                    string(values, "quietStart"),
                    string(values, "quietEnd"),
                    string(values, "timezone")), "redis");
        } catch (RuntimeException exception) {
            replace(fallback(), "fallback");
        }
    }

    private void replace(Window next, String nextSource) {
        boolean changed = !next.equals(snapshot) || !nextSource.equals(source);
        snapshot = next;
        source = nextSource;
        if (changed) {
            listeners.forEach(Runnable::run);
        }
    }

    private Window fallback() {
        return validate(
                properties.isQuietWindowEnabled(),
                properties.getQuietStart(),
                properties.getQuietEnd(),
                properties.getTimezone());
    }

    private static Window validate(
            boolean enabled, String quietStart, String quietEnd, String timezone) {
        try {
            LocalTime start = LocalTime.parse(quietStart);
            LocalTime end = LocalTime.parse(quietEnd);
            if (start.equals(end)) {
                throw new IllegalArgumentException(
                        "quietStart and quietEnd must define a non-zero window");
            }
            return new Window(enabled, start, end, ZoneId.of(timezone));
        } catch (DateTimeException | NullPointerException exception) {
            throw new IllegalArgumentException(
                    "Use HH:mm times and an IANA timezone such as Africa/Cairo", exception);
        }
    }

    private static boolean isQuietAt(Window window, Instant instant) {
        if (!window.enabled()) {
            return false;
        }
        LocalTime now = instant.atZone(window.timezone()).toLocalTime();
        if (window.quietStart().isBefore(window.quietEnd())) {
            return !now.isBefore(window.quietStart()) && now.isBefore(window.quietEnd());
        }
        return !now.isBefore(window.quietStart()) || now.isBefore(window.quietEnd());
    }

    private static Instant nextTransitionAfter(Window window, Instant instant) {
        if (!window.enabled()) {
            return null;
        }
        ZonedDateTime now = instant.atZone(window.timezone());
        LocalDate date = now.toLocalDate();
        boolean quiet = isQuietAt(window, instant);
        LocalTime transitionTime = quiet ? window.quietEnd() : window.quietStart();
        ZonedDateTime candidate = date.atTime(transitionTime).atZone(window.timezone());
        if (!candidate.toInstant().isAfter(instant)) {
            candidate = date.plusDays(1).atTime(transitionTime).atZone(window.timezone());
        }
        return candidate.toInstant();
    }

    private static String string(Map<Object, Object> values, String key) {
        Object value = values.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing operating-window field: " + key);
        }
        return value.toString();
    }

    private record Window(
            boolean enabled, LocalTime quietStart, LocalTime quietEnd, ZoneId timezone) {
    }

    public record WindowView(
            boolean enabled,
            String quietStart,
            String quietEnd,
            String timezone,
            boolean quietNow,
            Instant nextTransition,
            String source) {
    }
}
