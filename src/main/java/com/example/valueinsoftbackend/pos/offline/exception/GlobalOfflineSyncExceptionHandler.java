package com.example.valueinsoftbackend.pos.offline.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exception handler scoped to the offline sync controller package.
 * Returns clean JSON error responses consistent with the platform's
 * existing ApiErrorResponse shape.
 */
@RestControllerAdvice(basePackages = "com.example.valueinsoftbackend.pos.offline")
@Slf4j
public class GlobalOfflineSyncExceptionHandler {

    @ExceptionHandler(OfflineSyncException.class)
    public ResponseEntity<Map<String, Object>> handleOfflineSyncException(OfflineSyncException ex) {
        log.warn("OfflineSyncException [{}]: {}", ex.getErrorCode(), ex.getMessage());
        Map<String, Object> body = errorBody(ex.getErrorCode(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .toList();
        log.warn("Validation failed: {}", details);

        Map<String, Object> body = errorBody("VALIDATION_FAILED", "Request validation failed");
        body.put("details", details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) {
        log.error("Unexpected error in offline sync module", ex);
        Map<String, Object> body = errorBody("INTERNAL_ERROR", "An unexpected error occurred");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private Map<String, Object> errorBody(String errorCode, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("errorCode", errorCode);
        body.put("message", message);
        body.put("timestamp", Instant.now());
        return body;
    }
}
