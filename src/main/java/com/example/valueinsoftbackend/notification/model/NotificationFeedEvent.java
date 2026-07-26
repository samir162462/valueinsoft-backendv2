package com.example.valueinsoftbackend.notification.model;

import java.time.Instant;
import java.util.Map;

public record NotificationFeedEvent(
        long eventId,
        int sequenceNo,
        String typeKey,
        Map<String, Object> params,
        Integer actorUserId,
        String subjectType,
        Long subjectId,
        Instant contributedAt
) {
}
