package com.example.valueinsoftbackend.pos.offline.model;

import com.example.valueinsoftbackend.pos.offline.enums.PosIdempotencyStatus;

import java.time.Instant;

/**
 * Row model for the pos_idempotency_key table.
 */
public record PosIdempotencyModel(
        Long id,
        Long companyId,
        Long branchId,
        Long deviceId,
        String idempotencyKey,
        String offlineOrderNo,
        String requestHash,
        PosIdempotencyStatus status,
        Long officialOrderId,
        String officialInvoiceNo,
        Instant createdAt,
        Instant updatedAt
) {}
