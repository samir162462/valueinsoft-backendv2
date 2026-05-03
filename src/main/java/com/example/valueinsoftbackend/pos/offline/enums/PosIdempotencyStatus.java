package com.example.valueinsoftbackend.pos.offline.enums;

public enum PosIdempotencyStatus {
    PROCESSING,
    SYNCED,
    FAILED,
    DUPLICATE,
    NEEDS_REVIEW
}
