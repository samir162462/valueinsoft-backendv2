package com.example.valueinsoftbackend.notification.control;

import com.example.valueinsoftbackend.notification.config.NotificationControlProperties;
import com.example.valueinsoftbackend.notification.config.NotificationProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Phase 0 control gate: answers purely from static configuration.
 *
 * <p>Nothing here reads the database or Redis, which is what lets the application start while
 * PostgreSQL is suspended. The Redis-backed gate delivered in Phase 2 (NC-2.26) will be
 * annotated {@code @Primary} and will take precedence over this bean without any caller
 * change — injection is always by the {@link NotificationControlGate} interface.
 */
@Component
@Slf4j
public class StaticNotificationControlGate implements NotificationControlGate {

    private final NotificationControlProperties properties;
    private final NotificationProperties notificationProperties;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    public StaticNotificationControlGate(NotificationControlProperties properties,
                                         NotificationProperties notificationProperties) {
        this.properties = properties;
        this.notificationProperties = notificationProperties;
        log.info("Notification control gate: static configuration (Redis-backed gate not present)");
    }

    @Override
    public boolean isEnabled(ControlComponent component) {
        if (!notificationProperties.isEnabled()) {
            return false;
        }
        // A component that cannot be switched off is always on, regardless of the master
        // switch — otherwise a timed disable could never re-enable itself (invariant B-17).
        if (!component.switchable()) {
            return true;
        }
        if (!properties.isModule()) {
            return false;
        }
        return switch (component.scope()) {
            case "module"   -> NotificationComponent.PUBLISH.key().equals(component.key())
                               ? properties.isPublish() : properties.isModule();
            case "worker"   -> properties.workerEnabled(component.key());
            case "channel"  -> properties.channelEnabled(component.key());
            case "provider" -> properties.providerEnabled(component.key());
            case "api"      -> properties.apiEnabled(component.key());
            // Targeted scopes (tenant, category, type, branch) have no static form; they are
            // Redis-only and default to enabled until the Phase 2 gate takes over.
            default -> true;
        };
    }

    @Override
    public String suppressionMode(ControlComponent component) {
        return properties.getDefaultSuppressionMode();
    }

    @Override
    public void addChangeListener(Runnable listener) {
        listeners.add(listener);
    }

    @Override
    public String source() {
        return "static";
    }

    /** Exposed for tests: static configuration cannot change at runtime, but the plumbing can be exercised. */
    void notifyListeners() {
        listeners.forEach(Runnable::run);
    }
}
