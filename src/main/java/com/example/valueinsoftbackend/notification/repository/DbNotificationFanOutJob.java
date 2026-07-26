package com.example.valueinsoftbackend.notification.repository;

import com.example.valueinsoftbackend.notification.model.NotificationFanOutJob;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class DbNotificationFanOutJob {
    private final JdbcTemplate jdbc;

    public DbNotificationFanOutJob(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(long companyId, long eventId, Long broadcastId, int maxAttempts) {
        jdbc.update("""
                INSERT INTO %s (event_id, broadcast_id, max_attempts)
                VALUES (?, ?, ?)
                """.formatted(TenantSqlIdentifiers.notificationFanOutJobTable(companyId)),
                eventId, broadcastId, maxAttempts);
    }

    public Optional<NotificationFanOutJob> claimOne(long companyId, String workerId, int leaseSeconds) {
        String table = TenantSqlIdentifiers.notificationFanOutJobTable(companyId);
        String sql = """
                WITH candidate AS (
                    SELECT job_id FROM %s
                    WHERE status IN ('pending','failed')
                      AND next_attempt_at <= NOW()
                      AND attempt_count < max_attempts
                    ORDER BY next_attempt_at, job_id
                    FOR UPDATE SKIP LOCKED
                    LIMIT 1
                )
                UPDATE %s j
                SET status = 'claimed', claimed_by = ?, claimed_at = NOW(),
                    claim_expires_at = NOW() + make_interval(secs => ?),
                    attempt_count = attempt_count + 1
                FROM candidate c
                WHERE j.job_id = c.job_id
                RETURNING j.job_id, j.event_id, j.mode, j.bounded_audience,
                          j.fanout_cursor, j.attempt_count
                """.formatted(table, table);
        List<NotificationFanOutJob> rows = jdbc.query(sql, (rs, rowNum) ->
                        new NotificationFanOutJob(companyId, rs.getLong("job_id"),
                                rs.getLong("event_id"), rs.getString("mode"),
                                (Integer) rs.getObject("bounded_audience"),
                                (Integer) rs.getObject("fanout_cursor"),
                                rs.getInt("attempt_count")),
                workerId, leaseSeconds);
        return rows.stream().findFirst();
    }

    public void decideMode(NotificationFanOutJob job, String mode, Integer audience) {
        jdbc.update("""
                UPDATE %s SET mode = ?, bounded_audience = ?
                WHERE job_id = ? AND status = 'claimed' AND mode IS NULL
                """.formatted(TenantSqlIdentifiers.notificationFanOutJobTable(job.companyId())),
                mode, audience, job.jobId());
    }

    public void advance(NotificationFanOutJob job, int cursor, int created, boolean complete) {
        jdbc.update("""
                UPDATE %s
                SET fanout_cursor = ?, recipients_created = recipients_created + ?,
                    batches_processed = batches_processed + 1,
                    status = CASE WHEN ? THEN 'completed' ELSE 'claimed' END,
                    completed_at = CASE WHEN ? THEN NOW() ELSE completed_at END
                WHERE job_id = ?
                """.formatted(TenantSqlIdentifiers.notificationFanOutJobTable(job.companyId())),
                cursor, created, complete, complete, job.jobId());
    }

    public void heartbeat(NotificationFanOutJob job, int leaseSeconds) {
        jdbc.update("""
                UPDATE %s SET claim_expires_at = NOW() + make_interval(secs => ?)
                WHERE job_id = ? AND status = 'claimed'
                """.formatted(TenantSqlIdentifiers.notificationFanOutJobTable(job.companyId())),
                leaseSeconds, job.jobId());
    }

    public void fail(NotificationFanOutJob job, String error) {
        jdbc.update("""
                UPDATE %s
                SET status = CASE WHEN attempt_count >= max_attempts THEN 'dead' ELSE 'failed' END,
                    next_attempt_at = NOW() + make_interval(secs => LEAST(3600, 30 * attempt_count)),
                    last_error = LEFT(?, 2000), claimed_by = NULL, claim_expires_at = NULL
                WHERE job_id = ?
                """.formatted(TenantSqlIdentifiers.notificationFanOutJobTable(job.companyId())),
                error, job.jobId());
    }
}
