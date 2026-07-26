package com.example.valueinsoftbackend.notification.service;

import com.example.valueinsoftbackend.notification.config.NotificationProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;

@Component
public class NotificationBackoffPolicy {
    private final NotificationProperties properties;
    private final DoubleSupplier random;

    @Autowired
    public NotificationBackoffPolicy(NotificationProperties properties) {
        this(properties, () -> ThreadLocalRandom.current().nextDouble());
    }

    NotificationBackoffPolicy(NotificationProperties properties, DoubleSupplier random) {
        this.properties = properties;
        this.random = random;
    }

    public int delaySeconds(int attemptNumber, int retryAfterSeconds) {
        long[] schedule = properties.getDispatch().getBackoffSeconds();
        int index = Math.max(0, Math.min(schedule.length - 1, attemptNumber - 1));
        long base = schedule[index];
        double ratio = properties.getDispatch().getBackoffJitterRatio();
        double factor = (1.0d - ratio) + (random.getAsDouble() * ratio * 2.0d);
        long jittered = Math.max(0, Math.round(base * factor));
        return Math.toIntExact(Math.max(jittered, retryAfterSeconds));
    }
}
