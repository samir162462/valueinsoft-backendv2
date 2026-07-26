package com.example.valueinsoftbackend.notification.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PushOutboxItem(
        OffsetDateTime createdAt,
        long outboxId,
        UUID outboxUuid,
        byte[] deliveryKey,
        long companyId,
        long eventId,
        long recipientId,
        UUID recipientUuid,
        int userId,
        long deviceId,
        long deviceBindingVersion,
        String provider,
        String priority,
        String payloadJson,
        int payloadVersion,
        int payloadBytes,
        String collapseKey,
        int ttlSeconds,
        String status,
        int attemptCount,
        int maxAttempts
) {
}
