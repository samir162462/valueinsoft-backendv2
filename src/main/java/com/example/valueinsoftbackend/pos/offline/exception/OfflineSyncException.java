package com.example.valueinsoftbackend.pos.offline.exception;

/**
 * Domain exception for all offline sync pipeline errors.
 * Carries a machine-readable errorCode alongside the human message.
 */
public class OfflineSyncException extends RuntimeException {

    private final String errorCode;

    public OfflineSyncException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public OfflineSyncException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
