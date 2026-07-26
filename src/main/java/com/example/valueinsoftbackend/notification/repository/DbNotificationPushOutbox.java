package com.example.valueinsoftbackend.notification.repository;

import com.example.valueinsoftbackend.notification.model.NotificationDevice;
import com.example.valueinsoftbackend.notification.model.NotificationEvent;
import com.example.valueinsoftbackend.notification.model.PushOutboxItem;
import com.example.valueinsoftbackend.notification.service.PushPayloadBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class DbNotificationPushOutbox {
    private final JdbcTemplate jdbc;

    public DbNotificationPushOutbox(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean reserveAndInsert(byte[] deliveryKey,
                                    long companyId,
                                    int userId,
                                    long recipientId,
                                    UUID recipientUuid,
                                    NotificationEvent event,
                                    NotificationDevice device,
                                    PushPayloadBuilder.BuiltPush payload,
                                    int payloadVersion,
                                    int maxAttempts,
                                    String controlCancellationReason) {
        List<byte[]> reservation = jdbc.query("""
                INSERT INTO public.notification_delivery_dedup (
                    delivery_key, company_id, event_id, user_id, device_id,
                    channel, payload_version, expires_at
                ) VALUES (?, ?, ?, ?, ?, 'push', ?, NOW() + INTERVAL '30 days')
                ON CONFLICT (delivery_key) DO NOTHING
                RETURNING delivery_key
                """, (rs, rowNum) -> rs.getBytes(1),
                deliveryKey, companyId, event.eventId(), userId,
                device.deviceId(), payloadVersion);
        if (reservation.isEmpty()) {
            return false;
        }

        UUID outboxUuid = UUID.randomUUID();
        String cancelledReason = payload.rejected()
                ? "PAYLOAD_TOO_LARGE" : controlCancellationReason;
        String status = cancelledReason == null ? "pending" : "cancelled";
        jdbc.update("""
                INSERT INTO public.notification_push_outbox (
                    outbox_uuid, delivery_key, company_id, event_id,
                    recipient_id, recipient_uuid, user_id, device_id,
                    device_binding_version, provider, priority, payload,
                    payload_version, payload_bytes, collapse_key, ttl_seconds,
                    status, max_attempts, cancelled_reason
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb),
                          ?, ?, ?, 86400, ?, ?, ?)
                """, outboxUuid, deliveryKey, companyId, event.eventId(),
                recipientId, recipientUuid, userId, device.deviceId(),
                device.bindingVersion(), device.provider(), event.priority(),
                payload.json(), payloadVersion, payload.bytes(), recipientUuid.toString(),
                status, maxAttempts, cancelledReason);
        if (cancelledReason != null) {
            jdbc.update("""
                    INSERT INTO public.notification_delivery_attempt (
                        outbox_uuid, outbox_created_at, company_id, device_id,
                        provider, attempt_no, error_code, error_class,
                        payload_bytes, latency_ms
                    )
                    SELECT outbox_uuid, created_at, company_id, device_id,
                           provider, 1, ?, 'cancelled', payload_bytes, 0
                    FROM public.notification_push_outbox
                    WHERE outbox_uuid=?
                    """, cancelledReason, outboxUuid);
        }
        int attached = jdbc.update("""
                UPDATE public.notification_delivery_dedup
                SET outbox_uuid=?
                WHERE delivery_key=? AND outbox_uuid IS NULL
                """, outboxUuid, deliveryKey);
        if (attached != 1) {
            throw new IllegalStateException("Delivery reservation was not attached to its outbox");
        }
        return true;
    }

    public List<PushOutboxItem> claim(int batchSize, String workerId, int leaseSeconds) {
        return jdbc.query("""
                WITH candidates AS (
                    SELECT created_at, outbox_id
                    FROM public.notification_push_outbox
                    WHERE status IN ('pending','failed') AND next_attempt_at<=NOW()
                    ORDER BY public.notification_priority_rank(priority),
                             next_attempt_at, outbox_id
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED
                )
                UPDATE public.notification_push_outbox outbox
                SET status='claimed', claimed_by=?, claimed_at=NOW(),
                    claim_expires_at=NOW()+make_interval(secs=>?),
                    attempt_count=outbox.attempt_count+1
                FROM candidates
                WHERE outbox.created_at=candidates.created_at
                  AND outbox.outbox_id=candidates.outbox_id
                RETURNING outbox.*
                """, this::map, batchSize, workerId, leaseSeconds);
    }

    public void markSent(PushOutboxItem item, String providerMessageId) {
        jdbc.update("""
                UPDATE public.notification_push_outbox
                SET status='sent', sent_at=NOW(), provider_message_id=?,
                    claimed_by=NULL, claimed_at=NULL, claim_expires_at=NULL,
                    last_error_code=NULL, last_error=NULL
                WHERE created_at=? AND outbox_id=? AND status='claimed'
                """, providerMessageId, item.createdAt(), item.outboxId());
    }

    public void markFailed(PushOutboxItem item,
                           boolean dead,
                           String code,
                           String message,
                           int retryAfterSeconds) {
        jdbc.update("""
                UPDATE public.notification_push_outbox
                SET status=?, next_attempt_at=NOW()+make_interval(secs=>?),
                    claimed_by=NULL, claimed_at=NULL, claim_expires_at=NULL,
                    last_error_code=?, last_error=?
                WHERE created_at=? AND outbox_id=? AND status='claimed'
                """, dead ? "dead" : "failed", retryAfterSeconds,
                code, sanitize(message), item.createdAt(), item.outboxId());
    }

    public void cancel(PushOutboxItem item, String reason) {
        jdbc.update("""
                UPDATE public.notification_push_outbox
                SET status='cancelled', cancelled_reason=?,
                    claimed_by=NULL, claimed_at=NULL, claim_expires_at=NULL
                WHERE created_at=? AND outbox_id=? AND status='claimed'
                """, reason, item.createdAt(), item.outboxId());
    }

    public void requeueWithoutAttempt(PushOutboxItem item, String reason, int delaySeconds) {
        jdbc.update("""
                UPDATE public.notification_push_outbox
                SET status='failed', next_attempt_at=NOW()+make_interval(secs=>?),
                    attempt_count=GREATEST(0, attempt_count-1),
                    claimed_by=NULL, claimed_at=NULL, claim_expires_at=NULL,
                    last_error_code=?, last_error='Queued by notification control plane'
                WHERE created_at=? AND outbox_id=? AND status='claimed'
                """, delaySeconds, reason, item.createdAt(), item.outboxId());
    }

    public int releaseExpiredClaims(int batchSize, int retrySeconds, String reaperId) {
        return jdbc.update("""
                WITH expired AS (
                    SELECT created_at, outbox_id
                    FROM public.notification_push_outbox
                    WHERE status='claimed' AND claim_expires_at<NOW()
                    ORDER BY claim_expires_at
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED
                )
                UPDATE public.notification_push_outbox outbox
                SET status=CASE
                      WHEN attempt_count>=max_attempts THEN 'dead' ELSE 'failed' END,
                    claimed_by=NULL, claimed_at=NULL, claim_expires_at=NULL,
                    next_attempt_at=NOW()+make_interval(secs=>?),
                    last_error_code='LEASE_EXPIRED',
                    last_error=?
                FROM expired
                WHERE outbox.created_at=expired.created_at
                  AND outbox.outbox_id=expired.outbox_id
                """, batchSize, retrySeconds,
                "Reclaimed after lease expiry by " + reaperId);
    }

    public boolean currentPartitionExists() {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT to_regclass(
                    'public.notification_push_outbox_' || to_char(CURRENT_DATE, 'YYYY_MM')
                ) IS NOT NULL
                """, Boolean.class));
    }

    private PushOutboxItem map(java.sql.ResultSet rs, int rowNum)
            throws java.sql.SQLException {
        return new PushOutboxItem(
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getLong("outbox_id"),
                rs.getObject("outbox_uuid", UUID.class),
                rs.getBytes("delivery_key"),
                rs.getLong("company_id"),
                rs.getLong("event_id"),
                rs.getLong("recipient_id"),
                rs.getObject("recipient_uuid", UUID.class),
                rs.getInt("user_id"),
                rs.getLong("device_id"),
                rs.getLong("device_binding_version"),
                rs.getString("provider"),
                rs.getString("priority"),
                rs.getString("payload"),
                rs.getInt("payload_version"),
                rs.getInt("payload_bytes"),
                rs.getString("collapse_key"),
                rs.getInt("ttl_seconds"),
                rs.getString("status"),
                rs.getInt("attempt_count"),
                rs.getInt("max_attempts"));
    }

    private static String sanitize(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }
}
