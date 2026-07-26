package com.example.valueinsoftbackend.notification.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record NotificationFeedItem(
        UUID recipientUuid,
        long changeSequence,
        String typeKey,
        String category,
        String priority,
        String renderedTitle,
        String renderedBody,
        String renderedLocale,
        Integer templateVersion,
        String renderStatus,
        String deepLink,
        String subjectType,
        Long subjectId,
        Map<String, Object> params,
        int aggregateCount,
        String state,
        Instant createdAt,
        Instant lastEventAt,
        Integer branchId,
        @JsonIgnore String requiredCapability
) {
}
