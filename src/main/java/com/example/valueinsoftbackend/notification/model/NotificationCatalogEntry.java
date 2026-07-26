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
        boolean defaultChannelPush
) {
}
