package com.example.valueinsoftbackend.pos.offline.dto.response;

import java.util.List;

public record OfflineAdminImportListResponse(
        Long companyId,
        Long branchId,
        Long batchId,
        String status,
        String errorCode,
        int size,
        boolean hasMore,
        String nextCursor,
        List<OfflineAdminImportListItem> items
) {
}
