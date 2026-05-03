package com.example.valueinsoftbackend.pos.offline.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * Response for bootstrap data (products, prices, taxes, etc.)
 * delivered to POS devices for offline cache.
 */
public record BootstrapDataResponse(
        Long companyId,
        Long branchId,
        String dataType,
        Long versionNo,
        String checksum,
        Instant lastUpdatedAt,
        Instant serverTime,
        List<Object> data,
        Boolean hasMore,
        String nextCursor
) {}
