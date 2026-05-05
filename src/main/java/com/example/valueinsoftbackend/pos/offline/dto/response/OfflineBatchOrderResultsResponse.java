package com.example.valueinsoftbackend.pos.offline.dto.response;

import com.example.valueinsoftbackend.pos.offline.enums.OfflineOrderImportStatus;
import com.example.valueinsoftbackend.pos.offline.enums.PosSyncBatchStatus;

import java.time.Instant;
import java.util.List;

public record OfflineBatchOrderResultsResponse(
        Long batchId,
        Long companyId,
        Long branchId,
        PosSyncBatchStatus batchStatus,
        List<OfflineBatchOrderResultItem> orders
) {}
