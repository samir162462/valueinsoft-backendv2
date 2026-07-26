package com.example.valueinsoftbackend.notification.repository;

import com.example.valueinsoftbackend.notification.model.NotificationBroadcast.Batch;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Batch assignment and per-batch retry state (NC-7.5, NC-7.6).
 *
 * <p>Batches reference <em>targets</em>, not user-id ranges. Re-running a batch re-reads its
 * pending targets, so a retry reaches exactly the same people, and anyone already
 * materialised is absorbed by {@code pk_nbt} plus the tenant lineage key rather than being
 * sent to twice.
 */
@Repository
public class DbNotificationBroadcastBatch {

    private final JdbcTemplate jdbc;

    public DbNotificationBroadcastBatch(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<Batch> MAPPER = (rs, rowNum) -> new Batch(
            rs.getLong("batch_id"),
            rs.getLong("broadcast_id"),
            rs.getLong("company_id"),
            rs.getInt("batch_no"),
            rs.getInt("target_count"),
            rs.getString("status"),
            rs.getInt("attempt_count"),
            rs.getInt("max_attempts"));

    public void create(long broadcastId, long companyId, int batchNo, int targetCount, int maxAttempts) {
        jdbc.update("""
                INSERT INTO public.notification_broadcast_batch
                       (broadcast_id, company_id, batch_no, target_count, status, max_attempts)
                VALUES (?, ?, ?, ?, 'pending', ?)
                ON CONFLICT (broadcast_id, batch_no) DO NOTHING
                """, broadcastId, companyId, batchNo, targetCount, maxAttempts);
    }

    /** Standard claim: {@code SKIP LOCKED}, attempt incremented at claim time (§6.1). */
    public Optional<Batch> claim(String instanceId, int leaseSeconds) {
        return jdbc.query("""
                WITH claimed AS (
                    SELECT batch_id
                      FROM public.notification_broadcast_batch
                     WHERE status IN ('pending','failed')
                       AND next_attempt_at <= NOW()
                     ORDER BY next_attempt_at, batch_id
                     LIMIT 1
                       FOR UPDATE SKIP LOCKED
                )
                UPDATE public.notification_broadcast_batch b
                   SET status = 'claimed',
                       claimed_by = ?,
                       claimed_at = NOW(),
                       claim_expires_at = NOW() + make_interval(secs => ?),
                       attempt_count = b.attempt_count + 1
                  FROM claimed c
                 WHERE b.batch_id = c.batch_id
                RETURNING b.*
                """, MAPPER, instanceId, leaseSeconds).stream().findFirst();
    }

    public int reclaimExpired() {
        return jdbc.update("""
                UPDATE public.notification_broadcast_batch
                   SET status = CASE WHEN attempt_count >= max_attempts THEN 'dead' ELSE 'failed' END,
                       claimed_by = NULL, claimed_at = NULL, claim_expires_at = NULL,
                       next_attempt_at = NOW() + INTERVAL '60 seconds',
                       last_error = 'LEASE_EXPIRED'
                 WHERE status = 'claimed' AND claim_expires_at < NOW()
                """);
    }

    public void complete(long batchId, int materialized, int skipped, int outboxCreated) {
        jdbc.update("""
                UPDATE public.notification_broadcast_batch
                   SET status = 'completed',
                       materialized_count = ?, skipped_count = ?, outbox_created = ?,
                       completed_at = NOW(), claimed_by = NULL, claim_expires_at = NULL,
                       last_error = NULL
                 WHERE batch_id = ?
                """, materialized, skipped, outboxCreated, batchId);
    }

    /**
     * A failed batch materialised nothing — the whole batch is one transaction — so the retry
     * is safe and the backoff is simple.
     */
    public void fail(long batchId, String error, int backoffSeconds) {
        jdbc.update("""
                UPDATE public.notification_broadcast_batch
                   SET status = CASE WHEN attempt_count >= max_attempts THEN 'dead' ELSE 'failed' END,
                       claimed_by = NULL, claim_expires_at = NULL,
                       next_attempt_at = NOW() + make_interval(secs => ?),
                       last_error = ?
                 WHERE batch_id = ?
                """, backoffSeconds,
                error == null ? null : error.substring(0, Math.min(error.length(), 500)),
                batchId);
    }

    public int cancelPending(long broadcastId) {
        return jdbc.update("""
                UPDATE public.notification_broadcast_batch
                   SET status = 'cancelled', completed_at = NOW()
                 WHERE broadcast_id = ? AND status IN ('pending','failed')
                """, broadcastId);
    }

    public List<Batch> forBroadcast(long broadcastId) {
        return jdbc.query("""
                SELECT * FROM public.notification_broadcast_batch
                 WHERE broadcast_id = ? ORDER BY batch_no
                """, MAPPER, broadcastId);
    }

    /** Admin retry of a dead batch. Resets attempts so it gets a fresh set of tries. */
    public void requeueDead(long broadcastId, int batchNo) {
        jdbc.update("""
                UPDATE public.notification_broadcast_batch
                   SET status = 'pending', attempt_count = 0, next_attempt_at = NOW(),
                       last_error = NULL
                 WHERE broadcast_id = ? AND batch_no = ? AND status = 'dead'
                """, broadcastId, batchNo);
    }
}
