package com.example.valueinsoftbackend.notification.model;

public record NotificationFanOutJob(
        long companyId,
        long jobId,
        long eventId,
        String mode,
        Integer boundedAudience,
        Integer fanoutCursor,
        int attemptCount
) {
}
