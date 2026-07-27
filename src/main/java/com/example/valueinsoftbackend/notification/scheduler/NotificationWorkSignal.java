package com.example.valueinsoftbackend.notification.scheduler;

import com.example.valueinsoftbackend.notification.config.NotificationResourceSaverProperties;
import com.example.valueinsoftbackend.notification.control.NotificationComponent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * Redis pub/sub work signal used to wake parked queue consumers without polling PostgreSQL.
 */
@Service
@ConditionalOnProperty(name = "valueinsoft.notification.enabled", havingValue = "true")
public class NotificationWorkSignal {
    private final NotificationResourceSaverProperties properties;
    private final ObjectProvider<StringRedisTemplate> redisProvider;
    private final Map<NotificationComponent, Long> versions = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<BiConsumer<NotificationComponent, Long>> listeners =
            new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService delayedSignals =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "notif-work-signal-");
                thread.setDaemon(true);
                return thread;
            });

    public NotificationWorkSignal(
            NotificationResourceSaverProperties properties,
            ObjectProvider<StringRedisTemplate> redisProvider) {
        this.properties = properties;
        this.redisProvider = redisProvider;
    }

    @PostConstruct
    public void initialize() {
        refresh();
    }

    public void addListener(BiConsumer<NotificationComponent, Long> listener) {
        listeners.add(listener);
    }

    public long version(NotificationComponent component) {
        return versions.getOrDefault(component, 0L);
    }

    public void signal(NotificationComponent component) {
        long version = version(component) + 1;
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis != null) {
            try {
                Long persisted = redis.opsForHash().increment(
                        properties.getWorkKey(), component.key(), 1);
                if (persisted != null) {
                    version = persisted;
                }
                redis.convertAndSend(
                        properties.getWorkChannel(), component.key() + ":" + version);
            } catch (RuntimeException ignored) {
                // Local wake still preserves availability on a single instance.
            }
        }
        receive(component, version);
    }

    public void signalAfterCommit(NotificationComponent component, Duration delay) {
        Runnable schedule = () -> delayedSignals.schedule(
                () -> signal(component),
                Math.max(0, delay.toMillis()),
                TimeUnit.MILLISECONDS);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            schedule.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        schedule.run();
                    }
                });
    }

    public void receive(String payload) {
        if (payload == null) {
            return;
        }
        String[] parts = payload.split(":", 2);
        try {
            NotificationComponent component = NotificationComponent.valueOf(parts[0]);
            long version = parts.length == 2
                    ? Long.parseLong(parts[1])
                    : version(component) + 1;
            receive(component, version);
        } catch (IllegalArgumentException ignored) {
            // Ignore malformed or forward-version messages.
        }
    }

    public synchronized void refresh() {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null) {
            return;
        }
        try {
            redis.opsForHash().entries(properties.getWorkKey()).forEach((key, value) -> {
                try {
                    NotificationComponent component =
                            NotificationComponent.valueOf(key.toString());
                    versions.merge(component, Long.parseLong(value.toString()), Math::max);
                } catch (IllegalArgumentException ignored) {
                    // Ignore unknown fields written by newer application versions.
                }
            });
        } catch (RuntimeException ignored) {
            // Startup discovery cycles remain the recovery path when Redis is unavailable.
        }
    }

    private void receive(NotificationComponent component, long version) {
        long effective = versions.merge(component, version, Math::max);
        listeners.forEach(listener -> listener.accept(component, effective));
    }

    @PreDestroy
    void shutdown() {
        delayedSignals.shutdownNow();
    }
}
