package com.example.valueinsoftbackend.notification.repository;

import com.example.valueinsoftbackend.notification.model.NotificationBroadcast.Target;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

/**
 * The frozen audience snapshot (NC-7.4, ADR-12).
 *
 * <p>Inserts are idempotent on {@code pk_nbt (broadcast_id, company_id, user_id)}, which is
 * what makes re-planning after a crash reproduce an identical target set rather than a
 * doubled one.
 */
@Repository
public class DbNotificationBroadcastTarget {

    private final JdbcTemplate jdbc;

    public DbNotificationBroadcastTarget(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<Target> MAPPER = (rs, rowNum) -> new Target(
            rs.getLong("broadcast_id"),
            rs.getLong("company_id"),
            rs.getInt("user_id"),
            rs.getObject("branch_id") == null ? null : rs.getInt("branch_id"),
            rs.getInt("batch_no"),
            rs.getString("status"),
            rs.getString("skip_reason"),
            rs.getObject("recipient_uuid", UUID.class),
            rs.getInt("outbox_count"),
            rs.getTimestamp("processed_at") == null
                    ? null : rs.getTimestamp("processed_at").toInstant(),
            rs.getString("last_error"));

    /** One snapshotted recipient, before batch assignment. */
    public record NewTarget(int userId, Integer branchId, int batchNo) {
    }

    /**
     * Bulk insert in chunks. {@code ON CONFLICT DO NOTHING} means a re-planned broadcast
     * writes nothing new, so planning is safe to retry without a compensating delete.
     */
    public int insertAll(long broadcastId, long companyId, List<NewTarget> targets, int chunkSize) {
        int inserted = 0;
        for (int start = 0; start < targets.size(); start += chunkSize) {
            List<NewTarget> chunk = targets.subList(start, Math.min(start + chunkSize, targets.size()));
            int[] results = jdbc.batchUpdate("""
                    INSERT INTO public.notification_broadcast_target
                           (broadcast_id, company_id, user_id, branch_id, batch_no, status)
                    VALUES (?, ?, ?, ?, ?, 'pending')
                    ON CONFLICT (broadcast_id, company_id, user_id) DO NOTHING
                    """, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int index) throws SQLException {
                    NewTarget target = chunk.get(index);
                    ps.setLong(1, broadcastId);
                    ps.setLong(2, companyId);
                    ps.setInt(3, target.userId());
                    if (target.branchId() == null) {
                        ps.setNull(4, java.sql.Types.INTEGER);
                    } else {
                        ps.setInt(4, target.branchId());
                    }
                    ps.setInt(5, target.batchNo());
                }

                @Override
                public int getBatchSize() {
                    return chunk.size();
                }
            });
            for (int result : results) {
                if (result > 0) inserted += result;
            }
        }
        return inserted;
    }

    /** Authoritative targeted count — read from the rows actually present, never estimated. */
    public int countTargets(long broadcastId) {
        Integer value = jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.notification_broadcast_target WHERE broadcast_id = ?",
                Integer.class, broadcastId);
        return value == null ? 0 : value;
    }

    public int maxBatchNo(long broadcastId) {
        Integer value = jdbc.queryForObject(
                "SELECT COALESCE(MAX(batch_no), 0) FROM public.notification_broadcast_target"
                        + " WHERE broadcast_id = ?",
                Integer.class, broadcastId);
        return value == null ? 0 : value;
    }

    /** Pending targets for one batch. A retry re-reads exactly the same set. */
    public List<Target> pendingForBatch(long broadcastId, int batchNo) {
        return jdbc.query("""
                SELECT * FROM public.notification_broadcast_target
                 WHERE broadcast_id = ? AND batch_no = ? AND status = 'pending'
                 ORDER BY user_id
                """, MAPPER, broadcastId, batchNo);
    }

    /**
     * Optional status filter.
     *
     * <p>The {@code CAST(? AS TEXT)} is required, not decorative: PostgreSQL cannot infer the
     * type of a bare parameter used only as {@code ? IS NULL} and fails the statement with
     * "could not determine data type of parameter". This is the shape that only bites at
     * runtime, which is why the cast is here rather than left to be discovered in staging.
     */
    public List<Target> byStatus(long broadcastId, String status, int limit, int offset) {
        return jdbc.query("""
                SELECT * FROM public.notification_broadcast_target
                 WHERE broadcast_id = ?
                   AND (CAST(? AS TEXT) IS NULL OR status = CAST(? AS TEXT))
                 ORDER BY user_id
                 LIMIT ? OFFSET ?
                """, MAPPER, broadcastId, status, status, limit, offset);
    }

    public void markMaterialized(long broadcastId, long companyId, int userId,
                                 UUID recipientUuid, int outboxCount) {
        jdbc.update("""
                UPDATE public.notification_broadcast_target
                   SET status = 'materialized', recipient_uuid = ?, outbox_count = ?,
                       processed_at = NOW(), skip_reason = NULL, last_error = NULL
                 WHERE broadcast_id = ? AND company_id = ? AND user_id = ?
                """, recipientUuid, outboxCount, broadcastId, companyId, userId);
    }

    public void markSkipped(long broadcastId, long companyId, int userId, String reason) {
        jdbc.update("""
                UPDATE public.notification_broadcast_target
                   SET status = 'skipped', skip_reason = ?, processed_at = NOW()
                 WHERE broadcast_id = ? AND company_id = ? AND user_id = ?
                """, reason, broadcastId, companyId, userId);
    }

    public void markFailed(long broadcastId, long companyId, int userId, String error) {
        jdbc.update("""
                UPDATE public.notification_broadcast_target
                   SET status = 'failed', processed_at = NOW(), last_error = ?
                 WHERE broadcast_id = ? AND company_id = ? AND user_id = ?
                """, error == null ? null : error.substring(0, Math.min(error.length(), 500)),
                broadcastId, companyId, userId);
    }

    /**
     * Cancellation marks every remaining target skipped rather than deleting it — the
     * snapshot records intent, and "we intended to reach these 4,000 people and stopped"
     * is exactly what an operator needs to see afterwards.
     */
    public int cancelPending(long broadcastId) {
        return jdbc.update("""
                UPDATE public.notification_broadcast_target
                   SET status = 'skipped', skip_reason = 'broadcast_cancelled', processed_at = NOW()
                 WHERE broadcast_id = ? AND status = 'pending'
                """, broadcastId);
    }
}
