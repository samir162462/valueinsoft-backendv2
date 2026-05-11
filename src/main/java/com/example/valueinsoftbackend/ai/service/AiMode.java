package com.example.valueinsoftbackend.ai.service;

import java.util.Locale;

public enum AiMode {
    HELP,
    BUSINESS,
    SALES,
    INVENTORY,
    SUPPLIERS,
    CUSTOMERS,
    SHIFT,
    ADMIN;

    public static AiMode from(String value) {
        if (value == null || value.isBlank()) {
            return HELP;
        }
        try {
            return AiMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new AiPermissionException("AI_MODE_INVALID", "AI mode is not available");
        }
    }

    public boolean requiresBranch() {
        return this != HELP && this != ADMIN;
    }

    public boolean isAdmin() {
        return this == ADMIN;
    }
}
