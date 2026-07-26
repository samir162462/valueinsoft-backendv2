package com.example.valueinsoftbackend.notification.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record NotificationDeviceView(
        UUID deviceUuid,
        String installId,
        String provider,
        String appBundleId,
        String apnsEnvironment,
        String platform,
        long bindingVersion,
        String status,
        OffsetDateTime lastRotatedAt
) {
    public static NotificationDeviceView from(NotificationDevice device) {
        return new NotificationDeviceView(
                device.deviceUuid(),
                device.installId(),
                device.provider(),
                device.appBundleId(),
                device.apnsEnvironment(),
                device.platform(),
                device.bindingVersion(),
                device.status(),
                device.lastRotatedAt());
    }
}
