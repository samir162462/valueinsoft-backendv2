package com.example.valueinsoftbackend.notification.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record NotificationDevice(
        long deviceId,
        UUID deviceUuid,
        int userId,
        long companyId,
        Integer branchId,
        String installId,
        String provider,
        String appBundleId,
        String apnsEnvironment,
        String platform,
        long bindingVersion,
        byte[] encryptedCredential,
        String encryptionKeyId,
        byte[] credentialHash,
        String locale,
        String timezone,
        int payloadVersionMax,
        String status,
        int consecutiveFailures,
        OffsetDateTime invalidatedAt,
        OffsetDateTime lastRotatedAt
) {
    public boolean activeFor(long expectedCompanyId, int expectedUserId, long expectedBindingVersion) {
        return "active".equals(status)
                && companyId == expectedCompanyId
                && userId == expectedUserId
                && bindingVersion == expectedBindingVersion;
    }
}
