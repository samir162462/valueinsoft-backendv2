package com.example.valueinsoftbackend.pos.offline.exception;

import com.example.valueinsoftbackend.ExceptionPack.ApiErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

/**
 * Exception handler scoped to the offline sync controller package.
 * Returns clean JSON error responses consistent with the platform's
 * existing ApiErrorResponse shape.
 */
@RestControllerAdvice(basePackages = "com.example.valueinsoftbackend.pos.offline")
@Slf4j
public class GlobalOfflineSyncExceptionHandler {

    @ExceptionHandler(OfflineSyncException.class)
    public ResponseEntity<ApiErrorResponse> handleOfflineSyncException(OfflineSyncException ex) {
        log.warn("OfflineSyncException [{}]: {}", ex.getErrorCode(), ex.getMessage());
        HttpStatus status = resolveOfflineSyncStatus(ex.getErrorCode());
        return ResponseEntity.status(status).body(errorBody(status, ex.getErrorCode(), ex.getMessage(), null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .toList();
        log.warn("Validation failed: {}", details);

        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(errorBody(status, "VALIDATION_FAILED", "Request validation failed", details));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneral(Exception ex) {
        log.error("Unexpected error in offline sync module", ex);
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(status).body(errorBody(status, "INTERNAL_ERROR", "An unexpected error occurred", null));
    }

    private ApiErrorResponse errorBody(HttpStatus status, String errorCode, String message, List<String> details) {
        return new ApiErrorResponse(Instant.now(), status.value(), errorCode, message, null, details);
    }

    private HttpStatus resolveOfflineSyncStatus(String errorCode) {
        if (errorCode == null) return HttpStatus.INTERNAL_SERVER_ERROR;
        return switch (errorCode) {
            case "OFFLINE_SYNC_DISABLED", "DEVICE_OFFLINE_NOT_ALLOWED", "DEVICE_BLOCKED", "DEVICE_INACTIVE" -> HttpStatus.FORBIDDEN;
            case "DEVICE_NOT_REGISTERED", "BATCH_NOT_FOUND", "IMPORT_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "BATCH_TOO_LARGE", "VALIDATION_FAILED", "MISSING_IDEMPOTENCY_KEY",
                    "UNSUPPORTED_BOOTSTRAP_DATA_TYPE", "INVALID_BOOTSTRAP_CURSOR", "INVALID_SYNC_ERROR_CURSOR" -> HttpStatus.BAD_REQUEST;
            case "IDEMPOTENCY_PAYLOAD_MISMATCH" -> HttpStatus.CONFLICT;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
    }
}
