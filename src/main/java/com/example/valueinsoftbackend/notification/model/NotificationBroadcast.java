package com.example.valueinsoftbackend.notification.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Broadcast models (NOTIFICATION_CENTER_PLAN.md §3.9, ADR-12).
 *
 * <p>The defining property is that the audience is <strong>snapshotted at planning time</strong>.
 * Revision 2 stored user-id ranges, which is not a membership snapshot: a user created,
 * deactivated or moved between planning and materialisation changed the audience under the
 * operator's feet, so {@code targetedCount} was a number nobody could reproduce and a retried
 * batch could send to someone who was never targeted.
 */
public final class NotificationBroadcast {

    private NotificationBroadcast() {
    }

    /** What the admin asked for. Written by the API transaction and never edited afterwards. */
    public record Request(
            String scope,
            Long companyId,
            Integer branchId,
            String typeKey,
            Map<String, Object> audiencePredicate,
            Map<String, Object> params,
            String priority,
            String idempotencyKey,
            Instant scheduledAt,
            int createdByUserId,
            Integer confirmedByUserId,
            Integer approvedByUserId
    ) {
    }

    public record Row(
            long broadcastId,
            UUID broadcastUuid,
            String scope,
            Long companyId,
            Integer branchId,
            String typeKey,
            Map<String, Object> audiencePredicate,
            Map<String, Object> params,
            String priority,
            String status,
            Instant scheduledAt,
            Instant planningCompletedAt,
            int createdByUserId
    ) {
    }

    /**
     * The eight counters of §3.9.1, each with exactly one meaning.
     *
     * <p>{@code sentCount} is provider <em>acceptance</em>, not device display — the admin UI
     * must label it "Accepted by provider". Revision 2's {@code deliveredCount} implied the
     * latter and could not deliver it, which is why the name is gone.
     */
    public record Progress(
            UUID broadcastUuid,
            String status,
            int targetedCount,
            int materializedCount,
            int skippedCount,
            int outboxCreatedCount,
            int sentCount,
            int failedCount,
            int cancelledCount,
            int deadCount,
            int batchesTotal,
            int batchesCompleted,
            Instant planningCompletedAt,
            Instant completedAt,
            Instant cancelledAt,
            List<SkipBreakdown> skipBreakdown
    ) {
    }

    public record SkipBreakdown(String reason, int count) {
    }

    /** One intended recipient. Status moves pending → materialized | skipped | failed. */
    public record Target(
            long broadcastId,
            long companyId,
            int userId,
            Integer branchId,
            int batchNo,
            String status,
            String skipReason,
            UUID recipientUuid,
            int outboxCount,
            Instant processedAt,
            String lastError
    ) {
    }

    public record Batch(
            long batchId,
            long broadcastId,
            long companyId,
            int batchNo,
            int targetCount,
            String status,
            int attemptCount,
            int maxAttempts
    ) {
    }

    /** Reasons a snapshotted target is not materialised, matching {@code chk_nbt_skip_reason}. */
    public enum SkipReason {
        USER_INACTIVE("user_inactive"),
        LEFT_COMPANY("left_company"),
        CAPABILITY_REVOKED("capability_revoked"),
        NO_ACTIVE_DEVICE("no_active_device"),
        PREFERENCE_OPTED_OUT("preference_opted_out"),
        TYPE_DEPRECATED("type_deprecated"),
        BROADCAST_CANCELLED("broadcast_cancelled");

        private final String code;

        SkipReason(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}
