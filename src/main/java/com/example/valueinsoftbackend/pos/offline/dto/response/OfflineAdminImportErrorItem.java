package com.example.valueinsoftbackend.pos.offline.dto.response;

import java.time.Instant;

public record OfflineAdminImportErrorItem(
        Long errorId,
        String errorCode,
        String errorMessage,
        Instant createdAt
) {
}
