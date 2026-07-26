package com.example.valueinsoftbackend.notification.service;

import com.example.valueinsoftbackend.notification.model.NotificationRequest;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class NotificationIdempotencyService {
    private final CanonicalJsonService canonicalJson;

    public NotificationIdempotencyService(CanonicalJsonService canonicalJson) {
        this.canonicalJson = canonicalJson;
    }

    public byte[] fingerprint(NotificationRequest request) {
        String canonical = String.join("\u001f",
                Long.toString(request.companyId()),
                request.typeKey(),
                nullToEmpty(request.branchId()),
                nullToEmpty(request.actorUserId()),
                nullToEmpty(request.subjectType()),
                nullToEmpty(request.subjectId()),
                canonicalJson.canonicalize(request.params()),
                nullToEmpty(request.priority()),
                nullToEmpty(request.groupKey()),
                request.source(),
                nullToEmpty(request.broadcastId()),
                nullToEmpty(request.correlationId()));
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    public void assertSame(byte[] expected, byte[] actual, String idempotencyKey) {
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new NotificationIdempotencyConflictException(idempotencyKey);
        }
    }

    public String fingerprintHex(NotificationRequest request) {
        return HexFormat.of().formatHex(fingerprint(request));
    }

    private static String nullToEmpty(Object value) {
        return value == null ? "" : value.toString();
    }
}
