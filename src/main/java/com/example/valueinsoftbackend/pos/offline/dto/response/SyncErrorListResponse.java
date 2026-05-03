package com.example.valueinsoftbackend.pos.offline.dto.response;

import java.util.List;

public record SyncErrorListResponse(
        Long companyId,
        Long branchId,
        Long batchId,
        List<SyncErrorItemResponse> errors,
        Boolean hasMore,
        String nextCursor
) {
}
