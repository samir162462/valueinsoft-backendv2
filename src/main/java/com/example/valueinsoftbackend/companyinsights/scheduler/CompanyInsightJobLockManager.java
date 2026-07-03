package com.example.valueinsoftbackend.companyinsights.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.function.Supplier;

/**
 * Cross-instance concurrency guard for Company Smart Insights jobs.
 *
 * <p>Uses a Postgres session-level advisory lock held on a dedicated connection for the
 * duration of a job unit (keyed by job name + company), so overlapping runs across app
 * instances are skipped rather than duplicated. No ShedLock dependency required.
 *
 * <p>The work callback runs using its own pooled connection(s), preserving its own
 * per-date transaction boundaries. Every attempt is recorded in
 * {@code public.company_insight_job_run} for idempotency/observability.
 */
@Component
@Slf4j
public class CompanyInsightJobLockManager {

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    public CompanyInsightJobLockManager(DataSource dataSource, JdbcTemplate jdbcTemplate) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Run {@code work} for (job, company, businessDate) iff the advisory lock is acquired.
     *
     * @return true if the work executed, false if the lock was not acquired (skipped).
     */
    public boolean runExclusive(String jobName, int companyId, LocalDate businessDate, Supplier<Integer> work) {
        try (Connection lockConn = dataSource.getConnection()) {
            if (!tryAdvisoryLock(lockConn, jobName, companyId)) {
                recordSkipped(jobName, companyId, businessDate);
                log.info("Company insight job skipped (lock held) job={} companyId={} date={}",
                        jobName, companyId, businessDate);
                return false;
            }
            long ledgerId = recordRunning(jobName, companyId, businessDate);
            long startedAt = System.nanoTime();
            try {
                int rows = work.get();
                recordFinished(ledgerId, "SUCCESS", rows, null);
                log.info("Company insight job done job={} companyId={} date={} rows={} durationMs={}",
                        jobName, companyId, businessDate, rows, (System.nanoTime() - startedAt) / 1_000_000L);
                return true;
            } catch (RuntimeException exception) {
                recordFinished(ledgerId, "FAILED", 0, truncate(exception.getMessage()));
                log.warn("Company insight job failed job={} companyId={} date={} reason={}",
                        jobName, companyId, businessDate, exception.getMessage());
                throw exception;
            } finally {
                releaseAdvisoryLock(lockConn, jobName, companyId);
            }
        } catch (SQLException exception) {
            log.error("Company insight job lock error job={} companyId={} date={} reason={}",
                    jobName, companyId, businessDate, exception.getMessage());
            return false;
        }
    }

    private boolean tryAdvisoryLock(Connection connection, String jobName, int companyId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT pg_try_advisory_lock(hashtext(?)::int, ?)")) {
            ps.setString(1, jobName);
            ps.setInt(2, companyId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    private void releaseAdvisoryLock(Connection connection, String jobName, int companyId) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT pg_advisory_unlock(hashtext(?)::int, ?)")) {
            ps.setString(1, jobName);
            ps.setInt(2, companyId);
            ps.execute();
        } catch (SQLException exception) {
            log.warn("Advisory unlock failed job={} companyId={} reason={}", jobName, companyId, exception.getMessage());
        }
    }

    private long recordRunning(String jobName, int companyId, LocalDate businessDate) {
        Long id = jdbcTemplate.queryForObject(
                """
                        INSERT INTO public.company_insight_job_run
                            (job_name, company_id, business_date, run_key, status, started_at)
                        VALUES (?, ?, ?, ?, 'RUNNING', now())
                        ON CONFLICT (job_name, company_id, business_date, run_key) DO UPDATE
                        SET status = 'RUNNING', started_at = now(), finished_at = NULL, error = NULL
                        RETURNING id
                        """,
                Long.class,
                jobName, companyId, businessDate, runKey(jobName, businessDate)
        );
        return id == null ? -1L : id;
    }

    private void recordFinished(long ledgerId, String status, int rowsWritten, String error) {
        if (ledgerId < 0) {
            return;
        }
        jdbcTemplate.update(
                """
                        UPDATE public.company_insight_job_run
                        SET status = ?, finished_at = now(), rows_written = ?, error = ?
                        WHERE id = ?
                        """,
                status, rowsWritten, error, ledgerId
        );
    }

    private void recordSkipped(String jobName, int companyId, LocalDate businessDate) {
        jdbcTemplate.update(
                """
                        INSERT INTO public.company_insight_job_run
                            (job_name, company_id, business_date, run_key, status, started_at, finished_at)
                        VALUES (?, ?, ?, ?, 'SKIPPED', now(), now())
                        ON CONFLICT (job_name, company_id, business_date, run_key) DO UPDATE
                        SET status = 'SKIPPED', finished_at = now()
                        """,
                jobName, companyId, businessDate, runKey(jobName, businessDate)
        );
    }

    private String runKey(String jobName, LocalDate businessDate) {
        return jobName + "|" + (businessDate == null ? "-" : businessDate.toString());
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 2000 ? message : message.substring(0, 2000);
    }
}
