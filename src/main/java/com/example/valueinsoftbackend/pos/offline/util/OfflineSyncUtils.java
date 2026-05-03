package com.example.valueinsoftbackend.pos.offline.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Utility methods for the offline POS sync module.
 */
public final class OfflineSyncUtils {

    private OfflineSyncUtils() {
        // utility class
    }

    /**
     * Compute SHA-256 hash of the input string.
     * Used for payload hashing and idempotency key verification.
     */
    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Truncate a string to the given max length, appending "..." if truncated.
     * Useful for error messages stored in VARCHAR columns.
     */
    public static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 3) + "...";
    }
}
