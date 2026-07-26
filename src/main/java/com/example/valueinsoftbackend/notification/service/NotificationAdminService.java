package com.example.valueinsoftbackend.notification.service;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.notification.model.NotificationAdmin.DeliveryRow;
import com.example.valueinsoftbackend.notification.model.NotificationAdmin.DeviceInventoryRow;
import com.example.valueinsoftbackend.notification.model.NotificationAdmin.DeviceInventorySummary;
import com.example.valueinsoftbackend.notification.model.NotificationAdmin.ReplayCandidate;
import com.example.valueinsoftbackend.notification.model.NotificationAdmin.RetryResult;
import com.example.valueinsoftbackend.notification.repository.DbNotificationAdminSearch;
import com.example.valueinsoftbackend.notification.repository.DbNotificationAdminReplay;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Platform-admin delivery support (NC-7.12 to NC-7.15).
 *
 * <p><strong>Retry and resend are two different operations and are kept apart on purpose.</strong>
 * Conflating them was a real hazard in earlier revisions of the plan, because "replay" reads
 * either way:
 *
 * <ul>
 *   <li><em>Retry</em> re-attempts a delivery that already failed. It creates no event, no
 *       recipient row, and does not touch unread state. The user sees the notification they
 *       were always meant to see.</li>
 *   <li><em>Resend</em> creates a genuinely new notification. New event, new feed occurrence,
 *       unread again. It is the right tool when something must be re-communicated, and the
 *       wrong tool when a push simply failed.</li>
 * </ul>
 *
 * They hold separate capabilities so an operator cannot reach for the destructive one by
 * accident, and {@code resend} is not implied by {@code retry}.
 */
@Service
@Slf4j
public class NotificationAdminService {

    private final DbNotificationAdminSearch search;
    private final DbNotificationAdminReplay replay;
    private final NotificationRateLimiter rateLimiter;

    public NotificationAdminService(DbNotificationAdminSearch search,
                                    DbNotificationAdminReplay replay,
                                    NotificationRateLimiter rateLimiter) {
        this.search = search;
        this.replay = replay;
        this.rateLimiter = rateLimiter;
    }

    // ── Search (NC-7.12) ───────────────────────────────────────────────────

    public List<DeliveryRow> searchDeliveries(Instant from, Instant to, Long companyId,
                                              Integer userId, String status, String errorCode,
                                              Long broadcastId, int limit) {
        if (from == null || to == null || !to.isAfter(from)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "NOTIFICATION_ADMIN_RANGE_INVALID",
                    "A from/to range is required; the outbox is partitioned by created_at and an "
                            + "unbounded search scans every retained month");
        }
        return search.searchDeliveries(from, to, companyId, userId, status, errorCode,
                broadcastId, limit);
    }

    public List<Object[]> attempts(UUID outboxUuid, Instant createdAt) {
        return search.attemptsFor(outboxUuid, createdAt);
    }

    // ── Device inventory (NC-7.15) ─────────────────────────────────────────

    public DeviceInventorySummary deviceInventory(long companyId, int limit) {
        List<DeviceInventoryRow> devices = search.deviceInventory(companyId, limit);
        int active = 0;
        int stale = 0;
        int revoked = 0;
        for (DeviceInventoryRow device : devices) {
            switch (device.status()) {
                case "active" -> active++;
                case "stale" -> stale++;
                default -> revoked++;
            }
        }
        return new DeviceInventorySummary(companyId, active, stale, revoked, devices);
    }

    // ── Retry an existing delivery (NC-7.13) ───────────────────────────────

    /**
     * Creates a <strong>new</strong> outbox row referencing the same recipient, rather than
     * resetting the dead one.
     *
     * <p>Resetting would erase the evidence that the first attempt died, and a second retry
     * would be indistinguishable from the first. A new row with {@code replay_of_outbox_uuid}
     * and an incremented {@code replay_seq} makes double-retry visible in the data instead of
     * silent (§6.10).
     */
    @Transactional
    public RetryResult retryDelivery(UUID outboxUuid, Instant createdAtHint,
                                     String actor, String reason) {
        if (!rateLimiter.tryAcquire(0L, NotificationRateLimiter.ADMIN_RETRY, actor)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "NOTIFICATION_ADMIN_RETRY_RATE_LIMITED",
                    "Retry is limited to one call per minute per administrator");
        }
        if (reason == null || reason.isBlank()) {
            // A retry without a recorded reason is an unexplained push to a real person.
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "NOTIFICATION_ADMIN_REASON_REQUIRED",
                    "A reason is required so the retry is explicable afterwards");
        }

        ReplayCandidate candidate = search.loadReplayCandidate(outboxUuid, createdAtHint)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "NOTIFICATION_DELIVERY_NOT_FOUND",
                        "No delivery found for that identifier"));

        if (!candidate.isRetryable()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "NOTIFICATION_RETRY_NOT_ALLOWED", candidate.retryRefusalReason());
        }

        int nextSeq = candidate.replaySeq() + 1;

        // A distinct dedup key, derived from the original. Without it the reservation from the
        // first attempt would block the retry outright; with a plain new key, an accidental
        // double-retry would sail through. Deriving it makes each replay attempt idempotent in
        // its own right (§3.7.1).
        byte[] replayKey = replayDeliveryKey(candidate.deliveryKey(), nextSeq);

        UUID newUuid = replay.insertReplay(candidate, replayKey, nextSeq, actor, reason);
        search.annotateReplayed(candidate.outboxUuid(), candidate.createdAt(), newUuid);

        log.info("Delivery {} retried as {} (seq {}) by {}: {}",
                outboxUuid, newUuid, nextSeq, actor, reason);

        return new RetryResult(outboxUuid, newUuid, nextSeq,
                "Queued a new delivery attempt; no new notification was created");
    }

    /** {@code SHA-256(originalKey || '|replay|' || seq)} — §3.7.1. */
    private static byte[] replayDeliveryKey(byte[] original, int seq) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(original);
            digest.update(("|replay|" + seq).getBytes(StandardCharsets.UTF_8));
            return digest.digest();
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
