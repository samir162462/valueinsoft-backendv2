package com.example.valueinsoftbackend.notification.model;

public record NotificationPublishResult(long eventId, boolean created, boolean suppressed) {
    public static NotificationPublishResult suppressedResult() {
        return new NotificationPublishResult(0, false, true);
    }
}
