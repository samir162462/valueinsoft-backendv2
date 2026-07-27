package com.example.valueinsoftbackend.notification.control;

import com.example.valueinsoftbackend.notification.config.NotificationResourceSaverProperties;
import com.example.valueinsoftbackend.notification.repository.DbNotificationShutdownSchedule;
import com.example.valueinsoftbackend.notification.repository.DbNotificationShutdownSchedule.ScheduleChange;
import com.example.valueinsoftbackend.notification.repository.DbNotificationShutdownSchedule.ShutdownSchedule;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Platform-wide, Redis-backed shutdown schedules for notification database workers.
 *
 * <p>PostgreSQL is touched only by explicit platform-admin CRUD operations. Every worker gate
 * reads the in-memory snapshot, Redis pub/sub refreshes it, and an in-memory boundary timer
 * parks or re-arms the scheduler. Active schedules therefore issue no periodic database query.
 */
@Service
@ConditionalOnProperty(name = "valueinsoft.notification.enabled", havingValue = "true")
public class NotificationOperatingWindowService {
    private static final UUID FALLBACK_UUID = UUID.nameUUIDFromBytes(
            "notification-fallback-window".getBytes(StandardCharsets.UTF_8));
    private static final UUID LEGACY_UUID = UUID.nameUUIDFromBytes(
            "notification-legacy-window".getBytes(StandardCharsets.UTF_8));
    private static final String REDIS_SCHEDULES_FIELD = "schedules";

    private final NotificationResourceSaverProperties properties;
    private final ObjectProvider<StringRedisTemplate> redisProvider;
    private final DbNotificationShutdownSchedule repository;
    private final ObjectMapper objectMapper;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    private volatile List<Window> snapshot;
    private volatile String source = "fallback";

    @Autowired
    public NotificationOperatingWindowService(
            NotificationResourceSaverProperties properties,
            ObjectProvider<StringRedisTemplate> redisProvider,
            DbNotificationShutdownSchedule repository,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.redisProvider = redisProvider;
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.snapshot = fallback();
    }

    /** Test-only compatibility constructor. */
    NotificationOperatingWindowService(
            NotificationResourceSaverProperties properties,
            ObjectProvider<StringRedisTemplate> redisProvider) {
        this.properties = properties;
        this.redisProvider = redisProvider;
        this.repository = null;
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
        this.snapshot = fallback();
    }

    @PostConstruct
    public void initialize() {
        refresh();
    }

    public WindowView view() {
        List<Window> current = snapshot;
        Instant now = Instant.now();
        List<ScheduleView> schedules = current.stream()
                .map(this::toView)
                .toList();
        List<UUID> activeIds = current.stream()
                .filter(window -> isQuietAt(window, now))
                .map(Window::scheduleUuid)
                .toList();
        Window first = current.stream().findFirst().orElse(null);
        return new WindowView(
                current.stream().anyMatch(Window::enabled),
                first == null ? properties.getQuietStart() : first.quietStart().toString(),
                first == null ? properties.getQuietEnd() : first.quietEnd().toString(),
                first == null ? properties.getTimezone() : first.timezone().getId(),
                !activeIds.isEmpty(),
                nextTransitionAfter(current, now),
                source,
                schedules,
                activeIds);
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

    /**
     * Backward-compatible single-window endpoint. New clients use shutdown-schedule CRUD.
     * This state remains Redis-only so older deployments without V185 can still operate.
     */
    public synchronized WindowView update(
            boolean enabled, String quietStart, String quietEnd, String timezone) {
        Window requested = validateWindow(
                LEGACY_UUID, "Legacy quiet window", enabled, quietStart, quietEnd, timezone,
                Set.of(1, 2, 3, 4, 5, 6, 7), null, null,
                "Updated through the legacy operating-window endpoint");
        List<Window> next = new ArrayList<>(snapshot);
        next.removeIf(window -> window.scheduleUuid().equals(LEGACY_UUID)
                || window.scheduleUuid().equals(FALLBACK_UUID));
        next.add(requested);
        persistRedis(next);
        replace(List.copyOf(next), "redis");
        return view();
    }

    @Transactional
    public ScheduleView create(ScheduleRequest request, int actorUserId) {
        requireRepository();
        Window validated = validateRequest(UUID.randomUUID(), request);
        requireRedis();
        ShutdownSchedule created = repository.create(toChange(validated), actorUserId);
        publishDatabaseSnapshot();
        return toView(fromDatabase(created));
    }

    @Transactional
    public ScheduleView update(UUID scheduleUuid, ScheduleRequest request, int actorUserId) {
        requireRepository();
        Window validated = validateRequest(scheduleUuid, request);
        requireRedis();
        ShutdownSchedule updated = repository.update(
                scheduleUuid, toChange(validated), actorUserId);
        if (updated == null) {
            return null;
        }
        publishDatabaseSnapshot();
        return toView(fromDatabase(updated));
    }

    @Transactional
    public boolean delete(UUID scheduleUuid) {
        requireRepository();
        requireRedis();
        if (!repository.delete(scheduleUuid)) {
            return false;
        }
        publishDatabaseSnapshot();
        return true;
    }

    @Transactional(readOnly = true)
    public List<ScheduleView> resyncSchedulesFromDatabase() {
        requireRepository();
        requireRedis();
        List<Window> windows = repository.findAll().stream()
                .map(this::fromDatabase)
                .toList();
        persistRedis(windows);
        replace(windows, "redis");
        return windows.stream().map(this::toView).toList();
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
            Object schedulesJson = values.get(REDIS_SCHEDULES_FIELD);
            if (schedulesJson != null) {
                List<RedisSchedule> stored = objectMapper.readValue(
                        schedulesJson.toString(), new TypeReference<>() { });
                replace(stored.stream().map(this::fromRedis).toList(), "redis");
                return;
            }

            // Read the original one-window hash during a rolling deployment.
            replace(List.of(validateWindow(
                    LEGACY_UUID,
                    "Legacy quiet window",
                    Boolean.parseBoolean(string(values, "enabled")),
                    string(values, "quietStart"),
                    string(values, "quietEnd"),
                    string(values, "timezone"),
                    Set.of(1, 2, 3, 4, 5, 6, 7),
                    null,
                    null,
                    "Migrated from the legacy Redis operating window")), "redis");
        } catch (RuntimeException | JsonProcessingException exception) {
            replace(fallback(), "fallback");
        }
    }

    private void publishDatabaseSnapshot() {
        List<Window> windows = repository.findAll().stream()
                .map(this::fromDatabase)
                .toList();
        persistRedis(windows);
        replace(windows, "redis");
    }

    private void persistRedis(List<Window> windows) {
        StringRedisTemplate redis = requireRedis();
        try {
            String json = objectMapper.writeValueAsString(
                    windows.stream().map(this::toRedis).toList());
            redis.opsForHash().putAll(properties.getOperatingWindowKey(), Map.of(
                    REDIS_SCHEDULES_FIELD, json,
                    "formatVersion", "2"));
            redis.convertAndSend(properties.getOperatingWindowChannel(), "changed");
        } catch (RuntimeException | JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Redis is unavailable; shutdown schedules were not changed", exception);
        }
    }

    private StringRedisTemplate requireRedis() {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null) {
            throw new IllegalStateException(
                    "Redis is required to persist notification shutdown schedules");
        }
        return redis;
    }

    private void requireRepository() {
        if (repository == null) {
            throw new IllegalStateException("Shutdown-schedule persistence is unavailable");
        }
    }

    private void replace(List<Window> next, String nextSource) {
        List<Window> immutable = List.copyOf(next);
        boolean changed = !immutable.equals(snapshot) || !nextSource.equals(source);
        snapshot = immutable;
        source = nextSource;
        if (changed) {
            listeners.forEach(Runnable::run);
        }
    }

    private List<Window> fallback() {
        return List.of(validateWindow(
                FALLBACK_UUID,
                "Static fallback",
                properties.isQuietWindowEnabled(),
                properties.getQuietStart(),
                properties.getQuietEnd(),
                properties.getTimezone(),
                Set.of(1, 2, 3, 4, 5, 6, 7),
                null,
                null,
                "Static application configuration"));
    }

    private Window validateRequest(UUID scheduleUuid, ScheduleRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("A shutdown schedule is required");
        }
        return validateWindow(
                scheduleUuid,
                request.name(),
                request.enabled(),
                request.quietStart(),
                request.quietEnd(),
                request.timezone(),
                request.daysOfWeek(),
                request.effectiveFrom(),
                request.effectiveUntil(),
                request.reason());
    }

    private static Window validateWindow(
            UUID scheduleUuid,
            String name,
            boolean enabled,
            String quietStart,
            String quietEnd,
            String timezone,
            Set<Integer> daysOfWeek,
            LocalDate effectiveFrom,
            LocalDate effectiveUntil,
            String reason) {
        try {
            String cleanName = requireText(name, "name", 120);
            String cleanReason = requireText(reason, "reason", 500);
            LocalTime start = LocalTime.parse(quietStart);
            LocalTime end = LocalTime.parse(quietEnd);
            if (start.equals(end)) {
                throw new IllegalArgumentException(
                        "quietStart and quietEnd must define a non-zero window");
            }
            if (daysOfWeek == null || daysOfWeek.isEmpty()
                    || daysOfWeek.stream().anyMatch(day -> day == null || day < 1 || day > 7)) {
                throw new IllegalArgumentException(
                        "Select at least one weekday using ISO values 1 through 7");
            }
            if (effectiveFrom != null && effectiveUntil != null
                    && effectiveUntil.isBefore(effectiveFrom)) {
                throw new IllegalArgumentException(
                        "effectiveUntil cannot be before effectiveFrom");
            }
            return new Window(
                    Objects.requireNonNull(scheduleUuid),
                    cleanName,
                    enabled,
                    start,
                    end,
                    ZoneId.of(timezone),
                    Set.copyOf(new LinkedHashSet<>(daysOfWeek)),
                    effectiveFrom,
                    effectiveUntil,
                    cleanReason);
        } catch (DateTimeException | NullPointerException exception) {
            throw new IllegalArgumentException(
                    "Use HH:mm times and an IANA timezone such as Africa/Cairo", exception);
        }
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        String clean = value.trim();
        if (clean.length() > maxLength) {
            throw new IllegalArgumentException(
                    field + " must be at most " + maxLength + " characters");
        }
        return clean;
    }

    private static boolean isQuietAt(List<Window> windows, Instant instant) {
        return windows.stream().anyMatch(window -> isQuietAt(window, instant));
    }

    private static boolean isQuietAt(Window window, Instant instant) {
        if (!window.enabled()) {
            return false;
        }
        LocalDate localDate = instant.atZone(window.timezone()).toLocalDate();
        return activeForStartDate(window, localDate, instant)
                || activeForStartDate(window, localDate.minusDays(1), instant);
    }

    private static boolean activeForStartDate(
            Window window, LocalDate startDate, Instant instant) {
        if (!appliesOn(window, startDate)) {
            return false;
        }
        ZonedDateTime start = startDate.atTime(window.quietStart()).atZone(window.timezone());
        LocalDate endDate = window.quietStart().isBefore(window.quietEnd())
                ? startDate : startDate.plusDays(1);
        ZonedDateTime end = endDate.atTime(window.quietEnd()).atZone(window.timezone());
        return !instant.isBefore(start.toInstant()) && instant.isBefore(end.toInstant());
    }

    private static boolean appliesOn(Window window, LocalDate date) {
        return window.daysOfWeek().contains(date.getDayOfWeek().getValue())
                && (window.effectiveFrom() == null || !date.isBefore(window.effectiveFrom()))
                && (window.effectiveUntil() == null || !date.isAfter(window.effectiveUntil()));
    }

    private static Instant nextTransitionAfter(List<Window> windows, Instant instant) {
        boolean currentlyQuiet = isQuietAt(windows, instant);
        List<Instant> boundaries = new ArrayList<>();
        for (Window window : windows) {
            if (!window.enabled()) {
                continue;
            }
            LocalDate currentDate = instant.atZone(window.timezone()).toLocalDate();
            LocalDate firstDate = currentDate.minusDays(1);
            if (window.effectiveFrom() != null && window.effectiveFrom().isAfter(firstDate)) {
                firstDate = window.effectiveFrom();
            }
            LocalDate lastDate = currentDate.plusDays(400);
            if (window.effectiveUntil() != null && window.effectiveUntil().isBefore(lastDate)) {
                lastDate = window.effectiveUntil();
            }
            for (LocalDate date = firstDate; !date.isAfter(lastDate); date = date.plusDays(1)) {
                if (!appliesOn(window, date)) {
                    continue;
                }
                Instant start = date.atTime(window.quietStart())
                        .atZone(window.timezone()).toInstant();
                LocalDate endDate = window.quietStart().isBefore(window.quietEnd())
                        ? date : date.plusDays(1);
                Instant end = endDate.atTime(window.quietEnd())
                        .atZone(window.timezone()).toInstant();
                if (start.isAfter(instant)) {
                    boundaries.add(start);
                }
                if (end.isAfter(instant)) {
                    boundaries.add(end);
                }
            }
        }
        return boundaries.stream()
                .distinct()
                .sorted(Comparator.naturalOrder())
                .filter(boundary ->
                        isQuietAt(windows, boundary.plusMillis(1)) != currentlyQuiet)
                .findFirst()
                .orElse(null);
    }

    private Window fromDatabase(ShutdownSchedule row) {
        return validateWindow(
                row.scheduleUuid(),
                row.name(),
                row.enabled(),
                row.quietStart().toString(),
                row.quietEnd().toString(),
                row.timezone(),
                row.daysOfWeek(),
                row.effectiveFrom(),
                row.effectiveUntil(),
                row.reason());
    }

    private ScheduleChange toChange(Window window) {
        return new ScheduleChange(
                window.name(),
                window.enabled(),
                window.quietStart(),
                window.quietEnd(),
                window.timezone().getId(),
                window.daysOfWeek(),
                window.effectiveFrom(),
                window.effectiveUntil(),
                window.reason());
    }

    private ScheduleView toView(Window window) {
        return new ScheduleView(
                window.scheduleUuid(),
                window.name(),
                window.enabled(),
                window.quietStart().toString(),
                window.quietEnd().toString(),
                window.timezone().getId(),
                window.daysOfWeek(),
                window.effectiveFrom(),
                window.effectiveUntil(),
                window.reason(),
                isQuietAt(window, Instant.now()));
    }

    private RedisSchedule toRedis(Window window) {
        return new RedisSchedule(
                window.scheduleUuid().toString(),
                window.name(),
                window.enabled(),
                window.quietStart().toString(),
                window.quietEnd().toString(),
                window.timezone().getId(),
                window.daysOfWeek(),
                window.effectiveFrom() == null ? null : window.effectiveFrom().toString(),
                window.effectiveUntil() == null ? null : window.effectiveUntil().toString(),
                window.reason());
    }

    private Window fromRedis(RedisSchedule stored) {
        return validateWindow(
                UUID.fromString(stored.scheduleUuid()),
                stored.name(),
                stored.enabled(),
                stored.quietStart(),
                stored.quietEnd(),
                stored.timezone(),
                stored.daysOfWeek(),
                stored.effectiveFrom() == null ? null : LocalDate.parse(stored.effectiveFrom()),
                stored.effectiveUntil() == null ? null : LocalDate.parse(stored.effectiveUntil()),
                stored.reason());
    }

    private static String string(Map<Object, Object> values, String key) {
        Object value = values.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing operating-window field: " + key);
        }
        return value.toString();
    }

    private record Window(
            UUID scheduleUuid,
            String name,
            boolean enabled,
            LocalTime quietStart,
            LocalTime quietEnd,
            ZoneId timezone,
            Set<Integer> daysOfWeek,
            LocalDate effectiveFrom,
            LocalDate effectiveUntil,
            String reason) {
    }

    private record RedisSchedule(
            String scheduleUuid,
            String name,
            boolean enabled,
            String quietStart,
            String quietEnd,
            String timezone,
            Set<Integer> daysOfWeek,
            String effectiveFrom,
            String effectiveUntil,
            String reason) {
    }

    public record ScheduleRequest(
            String name,
            boolean enabled,
            String quietStart,
            String quietEnd,
            String timezone,
            Set<Integer> daysOfWeek,
            LocalDate effectiveFrom,
            LocalDate effectiveUntil,
            String reason) {
    }

    public record ScheduleView(
            UUID scheduleUuid,
            String name,
            boolean enabled,
            String quietStart,
            String quietEnd,
            String timezone,
            Set<Integer> daysOfWeek,
            LocalDate effectiveFrom,
            LocalDate effectiveUntil,
            String reason,
            boolean activeNow) {
    }

    public record WindowView(
            boolean enabled,
            String quietStart,
            String quietEnd,
            String timezone,
            boolean quietNow,
            Instant nextTransition,
            String source,
            List<ScheduleView> schedules,
            List<UUID> activeScheduleIds) {
    }
}
