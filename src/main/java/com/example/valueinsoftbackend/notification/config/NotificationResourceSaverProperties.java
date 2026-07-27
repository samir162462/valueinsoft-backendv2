package com.example.valueinsoftbackend.notification.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Redis-backed notification resource-saving configuration.
 *
 * <p>The operating window is platform-wide because a personal quiet-hours preference cannot
 * safely park shared workers: other users may still be eligible for delivery. Personal quiet
 * hours remain a delivery preference; this window controls database-worker activity.
 */
@Component
@ConfigurationProperties(prefix = "valueinsoft.notification.resource-saver")
@Getter
@Setter
public class NotificationResourceSaverProperties {

    /** Makes queue consumers wake on work signals and park after draining. */
    private boolean eventDriven = true;

    private String workKey = "notif:0:work:versions";
    private String workChannel = "notif:0:work:available";

    private String operatingWindowKey = "notif:0:operating-window";
    private String operatingWindowChannel = "notif:0:operating-window:changed";

    /** Static fallback when Redis has no operating-window state. */
    private boolean quietWindowEnabled = false;
    private String quietStart = "22:00";
    private String quietEnd = "07:00";
    private String timezone = "Africa/Cairo";
}
