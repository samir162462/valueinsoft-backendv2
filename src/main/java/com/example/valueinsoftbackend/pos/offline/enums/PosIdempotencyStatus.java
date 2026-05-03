package com.example.valueinsoftbackend.pos.offline.enums;

public enum PosIdempotencyStatus {
    RECEIVED,
    PROCESSING,
    SYNCED,
    FAILED,
    DUPLICATE,
    NEEDS_REVIEW,
    PAYLOAD_MISMATCH
}
