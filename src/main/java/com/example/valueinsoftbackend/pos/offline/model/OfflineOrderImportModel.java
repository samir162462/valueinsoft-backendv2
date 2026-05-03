package com.example.valueinsoftbackend.pos.offline.model;

import com.example.valueinsoftbackend.pos.offline.enums.OfflineOrderImportStatus;

import java.time.Instant;

/**
 * Row model for the pos_offline_order_import table.
 */
public record OfflineOrderImportModel(
        Long id,
        Long syncBatchId,
        Long companyId,
        Long branchId,
        Long deviceId,
        Long cashierId,
        String offlineOrderNo,
        String idempotencyKey,
        Instant localOrderCreatedAt,
        String payloadJson,
        String payloadHash,
        OfflineOrderImportStatus status,
        Long officialOrderId,
        String officialInvoiceNo,
        String errorCode,
        String errorMessage,
        int retryCount,
        Instant createdAt,
        Instant processingStartedAt,
        Instant processedAt,
        Instant updatedAt
) {}
