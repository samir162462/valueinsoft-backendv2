package com.example.valueinsoftbackend.notification.repository;

import com.example.valueinsoftbackend.notification.model.NotificationAdmin.DeliveryRow;
import com.example.valueinsoftbackend.notification.model.NotificationAdmin.DeviceInventoryRow;
import com.example.valueinsoftbackend.notification.model.NotificationAdmin.ReplayCandidate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Platform-admin read models for delivery support (NC-7.12, NC-7.15).
 *
 * <p>Everything here reads {@code public} tables only — no tenant schema is touched, so a
 * support engineer can answer "did this send?" for any company without a tenant context.
 *
 * <p><strong>Push tokens are never selected.</strong> Not the ciphertext, not the hash. A
 * support tool is exactly where a credential leaks by accident (§11.1).
 */
@Repository
public class DbNotificationAdminSearch {

    private final JdbcTemplate jdbc;

    public DbNotificationAdminSearch(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static final RowMapper<DeliveryRow> DELIVERY_MAPPER = (rs, rowNum) -> new DeliveryRow(
            rs.getObject("outbox_uuid", UUID.class),
            instant(rs, "created_at"),
            rs.getLong("company_id"),
            rs.getInt("user_id"),
            rs.getLong("device_id"),
            rs.getObject("recipient_uuid", UUID.class),
            rs.getString("provider"),
            rs.getString("priority"),
            rs.getString("status"),
            rs.getInt("attempt_count"),
            rs.getInt("max_attempts"),
            instant(rs, "sent_at"),
            rs.getString("cancelled_reason"),
            rs.getString("last_error_code"),
            rs.getString("provider_message_id"),
            rs.getObject("broadcast_id") == null ? null : rs.getLong("broadcast_id"));

    /**
     * Delivery search across the partitioned outbox.
     *
     * <p>Every optional filter is written as {@code CAST(? AS …) IS NULL OR column = …} —
     * PostgreSQL cannot infer the type of a parameter used only in a null test, and a bare
     * {@code ? IS NULL} fails the statement at runtime while compiling cleanly.
     *
     * <p>The {@code created_at} bounds are mandatory rather than optional: without them the
     * planner cannot prune partitions and a support query becomes a full scan of every month
     * retained.
     */
    public List<DeliveryRow> searchDeliveries(Instant from,
                                              Instant to,
                                              Long companyId,
                                              Integer userId,
                                              String status,
                                              String errorCode,
                                              Long broadcastId,
                                              int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT outbox_uuid, created_at, company_id, user_id, device_id, recipient_uuid,
                       provider, priority, status, attempt_count, max_attempts, sent_at,
                       cancelled_reason, last_error_code, provider_message_id, broadcast_id
                  FROM public.notification_push_outbox
                 WHERE created_at >= ? AND created_at < ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(Timestamp.from(from));
        args.add(Timestamp.from(to));

        if (companyId != null) {
            sql.append(" AND company_id = ?");
            args.add(companyId);
        }
        if (userId != null) {
            sql.append(" AND user_id = ?");
            args.add(userId);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            args.add(status);
        }
        if (errorCode != null && !errorCode.isBlank()) {
            sql.append(" AND last_error_code = ?");
            args.add(errorCode);
        }
        if (broadcastId != null) {
            sql.append(" AND broadcast_id = ?");
            args.add(broadcastId);
        }

        sql.append(" ORDER BY created_at DESC, outbox_id DESC LIMIT ?");
        args.add(Math.min(Math.max(limit, 1), 500));

        return jdbc.query(sql.toString(), DELIVERY_MAPPER, args.toArray());
    }

    /** Attempt history for one delivery, so support can see the whole retry sequence. */
    public List<Object[]> attemptsFor(UUID outboxUuid, Instant outboxCreatedAt) {
        return jdbc.query("""
                SELECT attempted_at, attempt_no, http_status, error_code, error_class,
                       provider_message_id, retry_after_seconds, latency_ms
                  FROM public.notification_delivery_attempt
                 WHERE outbox_uuid = ?
                 ORDER BY attempted_at
                """, (rs, rowNum) -> new Object[]{
                        instant(rs, "attempted_at"), rs.getInt("attempt_no"),
                        rs.getObject("http_status"), rs.getString("error_code"),
                        rs.getString("error_class"), rs.getString("provider_message_id"),
                        rs.getObject("retry_after_seconds"), rs.getInt("latency_ms")},
                outboxUuid);
    }

    /**
     * Loads a dead row for retry, together with the live state of its device.
     *
     * <p>Retry must not resurrect a token the provider has already rejected, so the device's
     * current status and binding version come back with it and the service refuses anything
     * that is no longer {@code active} (§6.10).
     */
    public Optional<ReplayCandidate> loadReplayCandidate(UUID outboxUuid, Instant createdAtHint) {
        StringBuilder sql = new StringBuilder("""
                SELECT o.created_at, o.outbox_id, o.outbox_uuid, o.delivery_key, o.company_id,
                       o.event_id, o.recipient_id, o.recipient_uuid, o.user_id, o.device_id,
                       o.provider, o.priority, o.payload::text AS payload_json, o.payload_version,
                       o.payload_bytes, o.collapse_key, o.ttl_seconds, o.status, o.max_attempts,
                       o.broadcast_id, o.broadcast_target_id,
                       COALESCE(o.replay_seq, 0) AS replay_seq,
                       d.status AS device_status, d.binding_version AS device_binding_version,
                       d.user_id AS device_user_id, d.company_id AS device_company_id
                  FROM public.notification_push_outbox o
                  JOIN public.notification_device d ON d.device_id = o.device_id
                 WHERE o.outbox_uuid = ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(outboxUuid);
        if (createdAtHint != null) {
            // Partition pruning. Without the hint this scans every retained partition, which
            // is why the API returns created_at alongside the uuid in search results.
            sql.append(" AND o.created_at = ?");
            args.add(Timestamp.from(createdAtHint));
        }

        return jdbc.query(sql.toString(), (rs, rowNum) -> new ReplayCandidate(
                        instant(rs, "created_at"),
                        rs.getLong("outbox_id"),
                        rs.getObject("outbox_uuid", UUID.class),
                        rs.getBytes("delivery_key"),
                        rs.getLong("company_id"),
                        rs.getLong("event_id"),
                        rs.getLong("recipient_id"),
                        rs.getObject("recipient_uuid", UUID.class),
                        rs.getInt("user_id"),
                        rs.getLong("device_id"),
                        rs.getString("provider"),
                        rs.getString("priority"),
                        rs.getString("payload_json"),
                        rs.getInt("payload_version"),
                        rs.getInt("payload_bytes"),
                        rs.getString("collapse_key"),
                        rs.getInt("ttl_seconds"),
                        rs.getString("status"),
                        rs.getInt("max_attempts"),
                        rs.getObject("broadcast_id") == null ? null : rs.getLong("broadcast_id"),
                        rs.getObject("broadcast_target_id") == null
                                ? null : rs.getLong("broadcast_target_id"),
                        rs.getInt("replay_seq"),
                        rs.getString("device_status"),
                        rs.getLong("device_binding_version"),
                        rs.getInt("device_user_id"),
                        rs.getLong("device_company_id")),
                args.toArray()).stream().findFirst();
    }

    /**
     * Device inventory for one company (NC-7.15). No token material of any kind — the counts
     * and versions are what support actually needs, and a stale-device spike on one app
     * version is the signal that matters.
     */
    public List<DeviceInventoryRow> deviceInventory(long companyId, int limit) {
        return jdbc.query("""
                SELECT device_uuid, user_id, platform, provider, app_bundle_id, apns_environment,
                       app_version, os_version, payload_version_max, status, binding_version,
                       consecutive_failures, registered_at, last_seen_at, last_rotated_at,
                       revoked_at, revoked_reason
                  FROM public.notification_device
                 WHERE company_id = ?
                 ORDER BY last_seen_at DESC
                 LIMIT ?
                """, (rs, rowNum) -> new DeviceInventoryRow(
                        rs.getObject("device_uuid", UUID.class),
                        rs.getInt("user_id"),
                        rs.getString("platform"),
                        rs.getString("provider"),
                        rs.getString("app_bundle_id"),
                        rs.getString("apns_environment"),
                        rs.getString("app_version"),
                        rs.getString("os_version"),
                        rs.getInt("payload_version_max"),
                        rs.getString("status"),
                        rs.getLong("binding_version"),
                        rs.getInt("consecutive_failures"),
                        instant(rs, "registered_at"),
                        instant(rs, "last_seen_at"),
                        instant(rs, "last_rotated_at"),
                        instant(rs, "revoked_at"),
                        rs.getString("revoked_reason")),
                companyId, Math.min(Math.max(limit, 1), 1000));
    }

    /** Annotates the dead row with the uuid of the replay it produced, for the audit trail. */
    public void annotateReplayed(UUID deadUuid, Instant deadCreatedAt, UUID newUuid) {
        jdbc.update("""
                UPDATE public.notification_push_outbox
                   SET last_error = COALESCE(last_error, '') || ' | replayed_as=' || ?
                 WHERE outbox_uuid = ? AND created_at = ?
                """, newUuid.toString(), deadUuid, Timestamp.from(deadCreatedAt));
    }
}
