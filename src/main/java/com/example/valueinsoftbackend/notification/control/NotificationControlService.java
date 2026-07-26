package com.example.valueinsoftbackend.notification.control;

import com.example.valueinsoftbackend.notification.config.NotificationControlProperties;
import com.example.valueinsoftbackend.notification.repository.DbNotificationControl;
import com.example.valueinsoftbackend.notification.scheduler.NotificationWorkerTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Redis-backed, database-free notification control snapshot. Redis pub/sub calls
 * {@link #refresh()} immediately; CONTROL_EXPIRY provides the 60-second missed-message
 * backstop through the notification module's private scheduler.
 */
@Service
@Primary
@ConditionalOnProperty(name = "valueinsoft.notification.enabled", havingValue = "true")
@Slf4j
public class NotificationControlService
        implements NotificationControlGate, NotificationWorkerTask {
    private final NotificationControlProperties properties;
    private final ObjectProvider<StringRedisTemplate> redisProvider;
    private final DbNotificationControl repository;
    private final Map<String, Boolean> snapshot = new ConcurrentHashMap<>();
    private final Map<String, String> suppressionModes = new ConcurrentHashMap<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private volatile String source = "fallback";

    public NotificationControlService(NotificationControlProperties properties,
                                      ObjectProvider<StringRedisTemplate> redisProvider,
                                      DbNotificationControl repository) {
        this.properties = properties;
        this.redisProvider = redisProvider;
        this.repository = repository;
    }

    @Override
    public boolean isEnabled(ControlComponent component) {
        if (!component.switchable()) {
            return true;
        }
        if (!value(NotificationComponent.MODULE)) {
            return false;
        }
        return value(component);
    }

    @Override
    public String suppressionMode(ControlComponent component) {
        return suppressionModes.getOrDefault(
                field(component), properties.getDefaultSuppressionMode());
    }

    @Override
    public void addChangeListener(Runnable listener) {
        listeners.add(listener);
    }

    @Override
    public String source() {
        return source;
    }

    @Transactional
    public DbNotificationControl.ControlState set(ControlComponent component, boolean enabled) {
        return change(component, enabled, properties.getDefaultSuppressionMode(),
                enabled ? null : "Runtime control change", null, 0, null, null);
    }

    @Transactional
    public DbNotificationControl.ControlState change(ControlComponent component,
                                                     boolean enabled,
                                                     String suppressionMode,
                                                     String reason,
                                                     OffsetDateTime disabledUntil,
                                                     int actorUserId,
                                                     String actorIp,
                                                     Integer queueDepth) {
        if (!component.switchable()) {
            throw new IllegalArgumentException(component.key() + " cannot be disabled");
        }
        String normalizedSuppression = suppressionMode == null
                ? properties.getDefaultSuppressionMode()
                : suppressionMode.trim().toUpperCase();
        if (!Set.of("SUPPRESS", "QUEUE", "CANCEL").contains(normalizedSuppression)) {
            throw new IllegalArgumentException("suppressionMode must be SUPPRESS, QUEUE, or CANCEL");
        }
        String normalizedReason = reason == null || reason.isBlank() ? null : reason.trim();
        if (!enabled && normalizedReason == null) {
            throw new IllegalArgumentException("reason is required when disabling a component");
        }
        if (enabled && disabledUntil != null) {
            throw new IllegalArgumentException("disabledUntil is only valid while disabled");
        }
        DbNotificationControl.ControlState state = repository.change(
                component,
                enabled,
                normalizedSuppression,
                normalizedReason,
                disabledUntil,
                actorUserId,
                actorIp,
                queueDepth);
        afterCommit(() -> writeRedis(List.of(state)));
        return state;
    }

    /**
     * Explicit recovery path. Startup and worker gate reads never call PostgreSQL.
     */
    @Transactional(readOnly = true)
    public List<DbNotificationControl.ControlState> resyncFromDatabase() {
        List<DbNotificationControl.ControlState> states = repository.findAll();
        writeRedis(states);
        return states;
    }

    public synchronized void refresh() {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null) {
            useFallback();
            return;
        }
        try {
            Map<Object, Object> values = redis.opsForHash().entries(properties.getKeyPrefix());
            if (values.isEmpty()) {
                useFallback();
                return;
            }
            Map<String, Boolean> replacement = new ConcurrentHashMap<>();
            Map<String, String> replacementModes = new ConcurrentHashMap<>();
            values.forEach((key, value) -> {
                String field = key.toString();
                if (field.startsWith("mode:")) {
                    replacementModes.put(field.substring(5), value.toString());
                } else {
                    replacement.put(field, Boolean.parseBoolean(value.toString()));
                }
            });
            boolean changed = !replacement.equals(snapshot) || !"redis".equals(source);
            snapshot.clear();
            snapshot.putAll(replacement);
            suppressionModes.clear();
            suppressionModes.putAll(replacementModes);
            source = "redis";
            if (changed) {
                notifyListeners();
            }
        } catch (RuntimeException ex) {
            log.warn("Notification control Redis refresh failed; using static fallback: {}",
                    ex.toString());
            useFallback();
        }
    }

    @Override
    public ControlComponent component() {
        return NotificationComponent.CONTROL_EXPIRY;
    }

    @Override
    public Duration delay() {
        return Duration.ofMillis(properties.getSnapshotRefreshMs());
    }

    @Override
    public void runCycle() {
        refresh();
    }

    private boolean value(ControlComponent component) {
        return snapshot.getOrDefault(field(component), fallback(component));
    }

    private boolean fallback(ControlComponent component) {
        return switch (component.scope()) {
            case "module" -> NotificationComponent.PUBLISH.key().equals(component.key())
                    ? properties.isPublish() : properties.isModule();
            case "worker" -> properties.workerEnabled(component.key());
            case "channel" -> properties.channelEnabled(component.key());
            case "provider" -> properties.providerEnabled(component.key());
            case "api" -> properties.apiEnabled(component.key());
            default -> true;
        };
    }

    private void useFallback() {
        boolean changed = !"fallback".equals(source) || !snapshot.isEmpty();
        snapshot.clear();
        suppressionModes.clear();
        source = "fallback";
        if (changed) {
            notifyListeners();
        }
    }

    private void notifyListeners() {
        listeners.forEach(listener -> {
            try {
                listener.run();
            } catch (RuntimeException ex) {
                log.warn("Notification control listener failed: {}", ex.toString());
            }
        });
    }

    private static String field(ControlComponent component) {
        return component.scope() + ":" + component.key();
    }

    private void writeRedis(List<DbNotificationControl.ControlState> states) {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null) {
            log.error("Notification control was persisted, but Redis is unavailable; resync is required");
            return;
        }
        try {
            long version = 0;
            for (DbNotificationControl.ControlState state : states) {
                redis.opsForHash().put(
                        properties.getKeyPrefix(),
                        state.scope() + ":" + state.componentKey(),
                        Boolean.toString(state.enabled()));
                redis.opsForHash().put(
                        properties.getKeyPrefix(),
                        "mode:" + state.scope() + ":" + state.componentKey(),
                        state.suppressionMode());
                version = Math.max(version, state.controlVersion());
            }
            if (version > 0) {
                redis.opsForValue().set(properties.getKeyPrefix() + ":version", Long.toString(version));
            }
            redis.convertAndSend(properties.getChangeChannel(), Long.toString(version));
            refresh();
        } catch (RuntimeException exception) {
            log.error("Notification control was persisted, but Redis update failed; resync is required: {}",
                    exception.toString());
        }
    }

    private void afterCommit(Runnable runnable) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            runnable.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                runnable.run();
            }
        });
    }
}
