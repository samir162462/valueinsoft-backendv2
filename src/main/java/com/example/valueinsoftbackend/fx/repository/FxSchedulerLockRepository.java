package com.example.valueinsoftbackend.fx.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;

@Repository
public class FxSchedulerLockRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public FxSchedulerLockRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean tryAcquire(String lockName, String lockedBy, Duration ttl) {
        long ttlSeconds = Math.max(1L, ttl.toSeconds());
        List<String> acquired = jdbcTemplate.query(
                """
                INSERT INTO public.global_scheduler_lock (
                    lock_name, locked_until, locked_by, acquired_at, updated_at
                ) VALUES (
                    :lockName,
                    NOW() + (:ttlSeconds * INTERVAL '1 second'),
                    :lockedBy,
                    NOW(),
                    NOW()
                )
                ON CONFLICT (lock_name) DO UPDATE
                SET locked_until = NOW() + (:ttlSeconds * INTERVAL '1 second'),
                    locked_by = :lockedBy,
                    acquired_at = NOW(),
                    updated_at = NOW()
                WHERE public.global_scheduler_lock.locked_until <= NOW()
                RETURNING lock_name
                """,
                new MapSqlParameterSource()
                        .addValue("lockName", lockName)
                        .addValue("lockedBy", lockedBy)
                        .addValue("ttlSeconds", ttlSeconds),
                (rs, rowNum) -> rs.getString("lock_name")
        );
        return !acquired.isEmpty();
    }

    public void release(String lockName, String lockedBy) {
        jdbcTemplate.update(
                """
                UPDATE public.global_scheduler_lock
                SET locked_until = NOW(),
                    updated_at = NOW()
                WHERE lock_name = :lockName
                  AND locked_by = :lockedBy
                """,
                new MapSqlParameterSource()
                        .addValue("lockName", lockName)
                        .addValue("lockedBy", lockedBy)
        );
    }
}
