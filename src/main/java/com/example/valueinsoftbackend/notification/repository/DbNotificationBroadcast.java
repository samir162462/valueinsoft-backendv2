package com.example.valueinsoftbackend.notification.repository;

import com.example.valueinsoftbackend.notification.model.NotificationBroadcast.Progress;
import com.example.valueinsoftbackend.notification.model.NotificationBroadcast.Request;
import com.example.valueinsoftbackend.notification.model.NotificationBroadcast.Row;
import com.example.valueinsoftbackend.notification.model.NotificationBroadcast.SkipBreakdown;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Broadcast parent rows (NC-7.3, NC-7.7).
 *
 * <p>Counters are always updated with {@code +=} rather than absolute values, because
 * several batch workers commit concurrently and an absolute write would clobber whatever
 * landed between read and write.
 */
@Repository
public class DbNotificationBroadcast {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public DbNotificationBroadcast(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    private Map<String, Object> readJson(String value) {
        if (value == null || value.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(value, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String writeJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unserialisable broadcast payload", ex);
        }
    }

    private final RowMapper<Row> rowMapper = (rs, rowNum) -> new Row(
            rs.getLong("broadcast_id"),
            rs.getObject("broadcast_uuid", UUID.class),
            rs.getString("scope"),
            rs.getObject("company_id") == null ? null : rs.getLong("company_id"),
            rs.getObject("branch_id") == null ? null : rs.getInt("branch_id"),
            rs.getString("type_key"),
            readJson(rs.getString("audience_predicate")),
            readJson(rs.getString("params")),
            rs.getString("priority"),
            rs.getString("status"),
            instant(rs, "scheduled_at"),
            instant(rs, "planning_completed_at"),
            rs.getInt("created_by_user_id"));

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    // ── Create ─────────────────────────────────────────────────────────────

    /**
     * The API transaction writes <strong>only</strong> this row (§2.3, §C-12). No tenant
     * events, no targets, no batches — those are the planning worker's job, which is what
     * lets {@code POST /broadcast} return in milliseconds regardless of audience size.
     */
    public Optional<Row> create(Request request, byte[] fingerprint) {
        List<Row> created = jdbc.query("""
                INSERT INTO public.notification_broadcast
                       (scope, company_id, branch_id, type_key, audience_predicate, params,
                        priority, idempotency_key, request_fingerprint, status, scheduled_at,
                        created_by_user_id, confirmed_by_user_id, approved_by_user_id)
                VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, 'scheduled', ?, ?, ?, ?)
                ON CONFLICT (idempotency_key) DO NOTHING
                RETURNING *
                """,
                rowMapper,
                request.scope(), request.companyId(), request.branchId(), request.typeKey(),
                writeJson(request.audiencePredicate()), writeJson(request.params()),
                request.priority(), request.idempotencyKey(), fingerprint,
                request.scheduledAt() == null ? null : Timestamp.from(request.scheduledAt()),
                request.createdByUserId(), request.confirmedByUserId(), request.approvedByUserId());
        return created.stream().findFirst();
    }

    public Optional<Row> byIdempotencyKey(String idempotencyKey) {
        return jdbc.query("SELECT * FROM public.notification_broadcast WHERE idempotency_key = ?",
                rowMapper, idempotencyKey).stream().findFirst();
    }

    public Optional<byte[]> fingerprint(String idempotencyKey) {
        return jdbc.query("SELECT request_fingerprint FROM public.notification_broadcast"
                        + " WHERE idempotency_key = ?",
                        (rs, rowNum) -> rs.getBytes("request_fingerprint"), idempotencyKey)
                .stream().findFirst();
    }

    public Optional<Row> byUuid(UUID uuid) {
        return jdbc.query("SELECT * FROM public.notification_broadcast WHERE broadcast_uuid = ?",
                rowMapper, uuid).stream().findFirst();
    }

    // ── Planning claim ─────────────────────────────────────────────────────

    /**
     * Claims one broadcast whose scheduled time has arrived. {@code SKIP LOCKED} plus the
     * status transition in the same statement makes the claim atomic across instances.
     */
    public Optional<Row> claimForPlanning(String instanceId, int leaseSeconds) {
        return jdbc.query("""
                WITH claimed AS (
                    SELECT broadcast_id
                      FROM public.notification_broadcast
                     WHERE status = 'scheduled'
                       AND (scheduled_at IS NULL OR scheduled_at <= NOW())
                     ORDER BY COALESCE(scheduled_at, created_at)
                     LIMIT 1
                       FOR UPDATE SKIP LOCKED
                )
                UPDATE public.notification_broadcast b
                   SET status = 'planning',
                       planning_started_at = NOW(),
                       claimed_by = ?,
                       claim_expires_at = NOW() + make_interval(secs => ?)
                  FROM claimed c
                 WHERE b.broadcast_id = c.broadcast_id
                RETURNING b.*
                """, rowMapper, instanceId, leaseSeconds).stream().findFirst();
    }

    /** Releases a planning claim whose lease expired, so another instance can retry it. */
    public int reclaimExpiredPlanning() {
        return jdbc.update("""
                UPDATE public.notification_broadcast
                   SET status = 'scheduled', claimed_by = NULL, claim_expires_at = NULL
                 WHERE status = 'planning' AND claim_expires_at < NOW()
                """);
    }

    public void markPlanned(long broadcastId, int targetedCount, int batchesTotal) {
        jdbc.update("""
                UPDATE public.notification_broadcast
                   SET status = 'materializing',
                       targeted_count = ?,
                       batches_total = ?,
                       planning_completed_at = NOW(),
                       claimed_by = NULL,
                       claim_expires_at = NULL
                 WHERE broadcast_id = ?
                """, targetedCount, batchesTotal, broadcastId);
    }

    public void markFailed(long broadcastId, String error) {
        jdbc.update("""
                UPDATE public.notification_broadcast
                   SET status = 'failed', claimed_by = NULL, claim_expires_at = NULL
                 WHERE broadcast_id = ?
                """, broadcastId);
    }

    // ── Counters (always +=) ───────────────────────────────────────────────

    public void addBatchResult(long broadcastId, int materialized, int skipped, int outboxCreated) {
        jdbc.update("""
                UPDATE public.notification_broadcast
                   SET materialized_count   = materialized_count + ?,
                       skipped_count        = skipped_count + ?,
                       outbox_created_count = outbox_created_count + ?,
                       batches_completed    = batches_completed + 1
                 WHERE broadcast_id = ?
                """, materialized, skipped, outboxCreated, broadcastId);
    }

    /** Called from the dispatch result path via {@code notification_push_outbox.broadcast_id}. */
    public void addDeliveryResult(long broadcastId, int sent, int failed, int cancelled, int dead) {
        jdbc.update("""
                UPDATE public.notification_broadcast
                   SET sent_count      = sent_count + ?,
                       failed_count    = failed_count + ?,
                       cancelled_count = cancelled_count + ?,
                       dead_count      = dead_count + ?
                 WHERE broadcast_id = ?
                """, sent, failed, cancelled, dead, broadcastId);
    }

    /**
     * Completion is derived, not asserted: a broadcast is done when every batch has finished,
     * and {@code partially_failed} when any of them died. Computing it from the batch table
     * means a crashed worker cannot leave the parent permanently "materializing".
     */
    public void refreshCompletion(long broadcastId) {
        jdbc.update("""
                UPDATE public.notification_broadcast b
                   SET status = CASE
                           WHEN EXISTS (SELECT 1 FROM public.notification_broadcast_batch
                                         WHERE broadcast_id = b.broadcast_id
                                           AND status IN ('pending','claimed','failed'))
                               THEN 'materializing'
                           WHEN EXISTS (SELECT 1 FROM public.notification_broadcast_batch
                                         WHERE broadcast_id = b.broadcast_id AND status = 'dead')
                               THEN 'partially_failed'
                           ELSE 'completed'
                       END,
                       completed_at = CASE
                           WHEN NOT EXISTS (SELECT 1 FROM public.notification_broadcast_batch
                                             WHERE broadcast_id = b.broadcast_id
                                               AND status IN ('pending','claimed','failed'))
                               THEN COALESCE(b.completed_at, NOW())
                           ELSE b.completed_at
                       END
                 WHERE b.broadcast_id = ?
                   AND b.status IN ('materializing','partially_failed')
                """, broadcastId);
    }

    public void cancel(long broadcastId) {
        jdbc.update("""
                UPDATE public.notification_broadcast
                   SET status = 'cancelled', cancelled_at = NOW()
                 WHERE broadcast_id = ? AND status IN ('draft','scheduled','planning','materializing')
                """, broadcastId);
    }

    // ── Progress ───────────────────────────────────────────────────────────

    public Optional<Progress> progress(UUID uuid) {
        Optional<Progress> base = jdbc.query("""
                SELECT broadcast_uuid, broadcast_id, status, targeted_count, materialized_count,
                       skipped_count, outbox_created_count, sent_count, failed_count,
                       cancelled_count, dead_count, batches_total, batches_completed,
                       planning_completed_at, completed_at, cancelled_at
                  FROM public.notification_broadcast
                 WHERE broadcast_uuid = ?
                """, (rs, rowNum) -> new Progress(
                        rs.getObject("broadcast_uuid", UUID.class),
                        rs.getString("status"),
                        rs.getInt("targeted_count"),
                        rs.getInt("materialized_count"),
                        rs.getInt("skipped_count"),
                        rs.getInt("outbox_created_count"),
                        rs.getInt("sent_count"),
                        rs.getInt("failed_count"),
                        rs.getInt("cancelled_count"),
                        rs.getInt("dead_count"),
                        rs.getInt("batches_total"),
                        rs.getInt("batches_completed"),
                        instant(rs, "planning_completed_at"),
                        instant(rs, "completed_at"),
                        instant(rs, "cancelled_at"),
                        List.of()),
                uuid).stream().findFirst();

        return base.map(progress -> {
            // The skip breakdown is what the operator actually needs: "12,000 skipped" is
            // alarming, "12,000 skipped because they have no device" is a fact.
            List<SkipBreakdown> breakdown = jdbc.query("""
                    SELECT t.skip_reason, COUNT(*) AS total
                      FROM public.notification_broadcast_target t
                      JOIN public.notification_broadcast b ON b.broadcast_id = t.broadcast_id
                     WHERE b.broadcast_uuid = ? AND t.status = 'skipped'
                     GROUP BY t.skip_reason
                     ORDER BY total DESC
                    """, (rs, rowNum) -> new SkipBreakdown(
                            rs.getString("skip_reason"), rs.getInt("total")),
                    uuid);
            return new Progress(progress.broadcastUuid(), progress.status(),
                    progress.targetedCount(), progress.materializedCount(),
                    progress.skippedCount(), progress.outboxCreatedCount(),
                    progress.sentCount(), progress.failedCount(),
                    progress.cancelledCount(), progress.deadCount(),
                    progress.batchesTotal(), progress.batchesCompleted(),
                    progress.planningCompletedAt(), progress.completedAt(),
                    progress.cancelledAt(), breakdown);
        });
    }
}
