package com.example.valueinsoftbackend.pos.offline.dto.response;

import java.time.Instant;

public record OfflineAdminRecentEvent(
        String eventType,
        Instant createdAt,
        String actor,
        String reason,
        boolean blocked
) {
}
