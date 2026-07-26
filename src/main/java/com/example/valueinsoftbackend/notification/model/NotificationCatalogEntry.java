package com.example.valueinsoftbackend.notification.model;

public record NotificationCatalogEntry(
        String typeKey,
        String category,
        String defaultPriority,
        String pushPreviewPolicy,
        String groupKeyTemplate,
        int aggregationWindowSeconds,
        String deepLinkTemplate,
        String requiredCapability,
        int retentionDays,
        int previewMaxChars,
        boolean defaultChannelPush,
        boolean defaultChannelInApp,
        /**
         * False for security and system types the user must not be able to silence.
         * A PUT that tries to change one of these is rejected with 422 rather than
         * silently ignored (§7.1).
         */
        boolean userMutable,
        /**
         * Only the four allowlisted {@code critical} types (§6.5). Bypasses ValueINSoft
         * quiet hours and application DND — never iOS Focus, silent mode, OS DND, and
         * never Apple Critical Alerts, which are out of scope for v1.
         */
        boolean bypassesQuietHours,
        Integer producerRateLimitPerMin
) {
    /** Convenience for the preference evaluator: a critical type is never suppressible. */
    public boolean isCritical() {
        return "critical".equals(defaultPriority);
    }
}
