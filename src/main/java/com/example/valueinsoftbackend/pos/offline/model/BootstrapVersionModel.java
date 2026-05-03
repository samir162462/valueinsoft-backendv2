package com.example.valueinsoftbackend.pos.offline.model;

import java.time.Instant;

/**
 * Row model for the pos_bootstrap_version table.
 */
public record BootstrapVersionModel(
        Long id,
        Long companyId,
        Long branchId,
        String dataType,
        long versionNo,
        String checksum,
        Instant lastChangedAt,
        Instant createdAt,
        Instant updatedAt
) {}
