package com.example.valueinsoftbackend.notification.model;

public record RenderedNotification(
        String title,
        String body,
        String preview,
        String locale,
        int templateVersion,
        String renderStatus,
        String deepLink,
        String groupKey,
        String previewGeneric
) {
}
