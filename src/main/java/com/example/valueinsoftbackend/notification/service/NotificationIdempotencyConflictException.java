package com.example.valueinsoftbackend.notification.service;

public class NotificationIdempotencyConflictException extends RuntimeException {
    public NotificationIdempotencyConflictException(String idempotencyKey) {
        super("Notification idempotency key was reused with a different request: " + idempotencyKey);
    }
}
