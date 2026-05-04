package com.example.valueinsoftbackend.pos.offline.dto.response;

import java.time.Instant;

public record OfflineAdminImportAuditEvent(
        String eventType,
        Instant createdAt,
        String actor,
        String reason
) {
}
