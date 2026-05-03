package com.example.valueinsoftbackend.pos.offline.dto.response;

import com.example.valueinsoftbackend.pos.offline.enums.OfflineOrderImportStatus;

import java.util.List;

/**
 * Per-order sync result within a batch upload response.
 */
public record OfflineOrderSyncResult(
        String offlineOrderNo,
        String idempotencyKey,
        OfflineOrderImportStatus status,
        Long officialOrderId,
        String officialInvoiceNo,
        String errorCode,
        String errorMessage,
        List<String> warnings
) {}
