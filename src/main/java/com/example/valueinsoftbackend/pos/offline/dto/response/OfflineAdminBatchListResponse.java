package com.example.valueinsoftbackend.pos.offline.dto.response;

import java.util.List;

public record OfflineAdminBatchListResponse(
        Long companyId,
        Long branchId,
        String status,
        boolean activeOnly,
        int size,
        boolean hasMore,
        String nextCursor,
        List<OfflineAdminBatchListItem> items
) {
}
