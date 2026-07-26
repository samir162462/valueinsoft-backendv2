package com.example.valueinsoftbackend.notification.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;

@Service
public class NotificationCursorCodec {
    public String encode(Instant lastEventAt, long recipientId) {
        String plain = lastEventAt + "\u001f" + recipientId;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(plain.getBytes(StandardCharsets.UTF_8));
    }

    public Cursor decode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(value),
                    StandardCharsets.UTF_8);
            String[] parts = decoded.split("\u001f", -1);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid notification cursor");
            }
            long recipientId = Long.parseLong(parts[1]);
            if (recipientId <= 0) {
                throw new IllegalArgumentException("Invalid notification cursor");
            }
            return new Cursor(Instant.parse(parts[0]), recipientId);
        } catch (IllegalArgumentException | DateTimeParseException ex) {
            throw new IllegalArgumentException("Invalid notification cursor", ex);
        }
    }

    public record Cursor(Instant lastEventAt, long recipientId) {}
}
