package com.example.valueinsoftbackend.notification.model;

import java.time.Instant;
import java.util.Map;

public record NotificationEvent(
        long eventId,
        String typeKey,
        Integer branchId,
        Integer actorUserId,
        String subjectType,
        Long subjectId,
        Map<String, Object> params,
        String priority,
        String groupKey,
        String source,
        Long broadcastId,
        String correlationId,
        int retentionDays,
        Instant createdAt
) {
}
