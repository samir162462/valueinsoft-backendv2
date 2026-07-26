package com.example.valueinsoftbackend.notification.repository;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.notification.model.NotificationAdmin.ReplayCandidate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

/**
 * Writes the replacement delivery for an admin retry (NC-7.13).
 *
 * <p>The dedup reservation and the outbox insert happen in the caller's transaction, in that
 * order — the same invariant as ordinary fan-out (§C-7/8): a reservation must never exist
 * without its outbox row.
 */
@Repository
public class DbNotificationAdminReplay {

    private final JdbcTemplate jdbc;

    public DbNotificationAdminReplay(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @return the uuid of the new outbox row
     * @throws ApiException if the replay key is already reserved, which means this exact
     *                      retry attempt has already been queued
     */
    public UUID insertReplay(ReplayCandidate candidate, byte[] replayKey, int replaySeq,
                             String actor, String reason) {

        // 1 · Reserve. Losing this race means the same retry was already queued — by a
        //     double-click, or by two admins looking at the same dead-letter screen.
        List<byte[]> reserved = jdbc.query("""
                INSERT INTO public.notification_delivery_dedup
                       (delivery_key, company_id, event_id, user_id, device_id, channel,
                        payload_version, expires_at)
                VALUES (?, ?, ?, ?, ?, 'push', ?, NOW() + INTERVAL '30 days')
                ON CONFLICT (delivery_key) DO NOTHING
                RETURNING delivery_key
                """, (rs, rowNum) -> rs.getBytes("delivery_key"),
                replayKey, candidate.companyId(), candidate.eventId(), candidate.userId(),
                candidate.deviceId(), candidate.payloadVersion());

        if (reserved.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "NOTIFICATION_RETRY_ALREADY_QUEUED",
                    "This retry has already been queued");
        }

        // 2 · Insert the replacement row. attempt_count restarts at zero so the retry gets a
        //     full set of provider attempts, and the device binding version is re-read from
        //     the live device — the pre-send check will re-verify it again at dispatch (§6.6).
        List<UUID> created = jdbc.query("""
                INSERT INTO public.notification_push_outbox
                       (created_at, delivery_key, company_id, event_id, recipient_id,
                        recipient_uuid, user_id, device_id, device_binding_version, provider,
                        priority, payload, payload_version, payload_bytes, collapse_key,
                        ttl_seconds, status, attempt_count, max_attempts, next_attempt_at,
                        broadcast_id, broadcast_target_id, replay_of_outbox_uuid, replay_seq,
                        last_error)
                VALUES (NOW(), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, 'pending',
                        0, ?, NOW(), ?, ?, ?, ?, ?)
                RETURNING outbox_uuid
                """, (rs, rowNum) -> rs.getObject("outbox_uuid", UUID.class),
                replayKey,
                candidate.companyId(),
                candidate.eventId(),
                candidate.recipientId(),
                candidate.recipientUuid(),
                candidate.userId(),
                candidate.deviceId(),
                candidate.deviceBindingVersion(),
                candidate.provider(),
                candidate.priority(),
                candidate.payloadJson(),
                candidate.payloadVersion(),
                candidate.payloadBytes(),
                candidate.collapseKey(),
                candidate.ttlSeconds(),
                candidate.maxAttempts(),
                candidate.broadcastId(),
                candidate.broadcastTargetId(),
                candidate.outboxUuid(),
                replaySeq,
                "admin_retry by " + actor + ": "
                        + reason.substring(0, Math.min(reason.length(), 300)));

        UUID newUuid = created.stream().findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "NOTIFICATION_RETRY_INSERT_FAILED",
                        "Replay row was not created"));

        // 3 · Attach the outbox reference to the reservation, closing the loop.
        jdbc.update("""
                UPDATE public.notification_delivery_dedup
                   SET outbox_uuid = ?
                 WHERE delivery_key = ?
                """, newUuid, replayKey);

        return newUuid;
    }

    /** Used by the timestamp-hint variant of the search when an admin pastes a bare uuid. */
    public Timestamp createdAtOf(UUID outboxUuid) {
        return jdbc.query("""
                SELECT created_at FROM public.notification_push_outbox
                 WHERE outbox_uuid = ? AND created_at > NOW() - INTERVAL '90 days'
                 LIMIT 1
                """, (rs, rowNum) -> rs.getTimestamp("created_at"), outboxUuid)
                .stream().findFirst().orElse(null);
    }
}
