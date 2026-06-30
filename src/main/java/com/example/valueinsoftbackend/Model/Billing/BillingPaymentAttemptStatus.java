package com.example.valueinsoftbackend.Model.Billing;

import java.util.Locale;
import java.util.Set;

public enum BillingPaymentAttemptStatus {
    CREATED,
    CHECKOUT_PENDING,
    CHECKOUT_REQUESTED,
    PENDING_PROVIDER,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    EXPIRED,
    SUPERSEDED;

    private static final Set<BillingPaymentAttemptStatus> TERMINAL_STATUSES = Set.of(
            SUCCEEDED,
            FAILED,
            CANCELLED,
            EXPIRED,
            SUPERSEDED
    );

    public boolean isTerminal() {
        return TERMINAL_STATUSES.contains(this);
    }

    public String legacyValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static boolean isTerminal(String status) {
        BillingPaymentAttemptStatus parsed = parse(status);
        return parsed != null && parsed.isTerminal();
    }

    public static BillingPaymentAttemptStatus parse(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return BillingPaymentAttemptStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
