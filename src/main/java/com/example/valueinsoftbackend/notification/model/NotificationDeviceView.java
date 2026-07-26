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
        OffsetDateTime lastRotatedAt,
        /**
         * The numeric user this device is bound to. Returned so the mobile client can
         * complete the payload identity guard of NC-4.18: a push carries companyId and
         * userId (§7.5), and the client discards anything that does not match the active
         * session. The client has the username from its JWT but never the numeric id, so
         * without this field the guard could only check companyId. This is defence in
         * depth — the primary protection remains the server-side pre-send binding check
         * (§6.6) — but a half-implemented guard is worse than an explicit one.
         *
         * <p>Safe to expose: the caller is already authenticated as this user, and the
         * value is their own id.
         */
        long userId,
        long companyId
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
                device.lastRotatedAt(),
                device.userId(),
                device.companyId());
    }
}
