package com.example.valueinsoftbackend.pos.offline.dto.response;

import java.time.Instant;
import java.util.List;

public record BootstrapPage<T>(
        List<T> items,
        boolean hasMore,
        String nextCursor,
        Instant lastUpdatedAt
) {
}
