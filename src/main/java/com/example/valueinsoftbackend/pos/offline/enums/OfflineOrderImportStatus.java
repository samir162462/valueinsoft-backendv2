package com.example.valueinsoftbackend.pos.offline.enums;

public enum OfflineOrderImportStatus {
    PENDING,
    PENDING_RETRY,
    PROCESSING,
    READY_FOR_VALIDATION,
    VALIDATING,
    VALIDATED,
    VALIDATION_FAILED,
    POSTING,
    POSTING_FAILED,
    SYNCED,
    FAILED,
    DUPLICATE,
    NEEDS_REVIEW
}
