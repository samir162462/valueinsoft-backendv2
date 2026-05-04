package com.example.valueinsoftbackend.pos.offline.dto.response;

import java.time.Instant;
import java.util.List;

public record OfflineAdminErrorSummary(
        int totalErrors,
        Instant latestErrorAt,
        List<OfflineAdminErrorCodeCount> topErrorCodes
) {
}
