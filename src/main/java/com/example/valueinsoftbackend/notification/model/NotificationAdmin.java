package com.example.valueinsoftbackend.notification.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Platform-admin support models (NC-7.12, NC-7.13, NC-7.14, NC-7.15).
 *
 * <p>None of these carry push-token material — not the ciphertext, not the hash. A support
 * tool is precisely where a credential leaks by accident (§11.1).
 */
public final class NotificationAdmin {

    private NotificationAdmin() {
    }

    /**
     * One delivery in the search results.
     *
     * <p>{@code createdAt} travels alongside {@code outboxUuid} deliberately: the outbox is
     * partitioned on it, so returning it lets every follow-up action prune to one partition
     * instead of scanning ninety days.
     */
    public record DeliveryRow(
            UUID outboxUuid,
            Instant createdAt,
            long companyId,
            int userId,
            long deviceId,
            UUID recipientUuid,
            String provider,
            String priority,
            String status,
            int attemptCount,
            int maxAttempts,
            Instant sentAt,
            String cancelledReason,
            String lastErrorCode,
            /** FCM `name` or APNs `apns-id` — what a support ticket is traced with. */
            String providerMessageId,
            Long broadcastId
    ) {
    }

    /** A dead row plus the live state of its device, loaded before a retry is allowed. */
    public record ReplayCandidate(
            Instant createdAt,
            long outboxId,
            UUID outboxUuid,
            byte[] deliveryKey,
            long companyId,
            long eventId,
            long recipientId,
            UUID recipientUuid,
            int userId,
            long deviceId,
            String provider,
            String priority,
            String payloadJson,
            int payloadVersion,
            int payloadBytes,
            String collapseKey,
            int ttlSeconds,
            String status,
            int maxAttempts,
            Long broadcastId,
            Long broadcastTargetId,
            int replaySeq,
            String deviceStatus,
            long deviceBindingVersion,
            int deviceUserId,
            long deviceCompanyId
    ) {
        /**
         * Retry is only meaningful for a row that gave up, and only safe onto a device that is
         * still bound to the same person. Anything else and the retry would either duplicate a
         * live delivery or push to a token the provider already rejected (§6.10).
         */
        public boolean isRetryable() {
            return "dead".equals(status)
                    && "active".equals(deviceStatus)
                    && deviceUserId == userId
                    && deviceCompanyId == companyId;
        }

        public String retryRefusalReason() {
            if (!"dead".equals(status)) {
                return "Only a dead delivery can be retried; this one is '" + status + "'";
            }
            if (!"active".equals(deviceStatus)) {
                return "The target device is '" + deviceStatus + "' and can no longer receive push";
            }
            return "The device is now bound to a different user or company";
        }
    }

    public record DeviceInventoryRow(
            UUID deviceUuid,
            int userId,
            String platform,
            String provider,
            String appBundleId,
            String apnsEnvironment,
            String appVersion,
            String osVersion,
            /** Drives the payload-version decision in §7.5. */
            int payloadVersionMax,
            String status,
            long bindingVersion,
            int consecutiveFailures,
            Instant registeredAt,
            Instant lastSeenAt,
            Instant lastRotatedAt,
            Instant revokedAt,
            String revokedReason
    ) {
    }

    public record DeviceInventorySummary(
            long companyId,
            int active,
            int stale,
            int revoked,
            List<DeviceInventoryRow> devices
    ) {
    }

    /** Result of a retry — deliberately not shaped like a resend, so they cannot be confused. */
    public record RetryResult(
            UUID originalOutboxUuid,
            UUID newOutboxUuid,
            int replaySeq,
            String message
    ) {
    }

    public record ResendResult(
            UUID recipientUuid,
            long eventId,
            String message
    ) {
    }
}
