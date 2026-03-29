package com.example.valueinsoftbackend.DatabaseRequests.DbSQL;

/*
 * Copyright (c) Samir Filifl
 */


import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Date;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "Scheduling.enable" , matchIfMissing = true)
@Slf4j
public class DbSqlCloseIdles {

    private static final String TERMINATE_IDLE_SQL =
            "WITH inactive_connections AS ( " +
                    "SELECT pid " +
                    "FROM pg_stat_activity " +
                    "WHERE pid <> pg_backend_pid() " +
                    "AND application_name !~ '(?:psql)|(?:pgAdmin.+)' " +
                    "AND state in ('idle', 'idle in transaction', 'idle in transaction (aborted)', 'disabled') " +
                    "AND current_timestamp - state_change > interval '9 seconds' " +
                    ") " +
                    "SELECT pg_terminate_backend(pid) FROM inactive_connections";

    private final JdbcTemplate jdbcTemplate;

    public DbSqlCloseIdles(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(initialDelay = 60_000, fixedDelayString = "${terminate.delay}")
    public void terminateDatabaseIdleProcess() {
        log.debug("Running idle connection cleanup at {}", new Date());
        terminate();
    }

    public boolean terminate() {
        try {
            List<Boolean> terminatedRows = jdbcTemplate.query(
                    TERMINATE_IDLE_SQL,
                    (rs, rowNum) -> rs.getBoolean(1)
            );
            long terminatedCount = terminatedRows.stream().filter(Boolean.TRUE::equals).count();
            log.debug("Idle connection cleanup executed, terminated {} backend sessions", terminatedCount);
        } catch (Exception exception) {
            log.warn("Idle connection cleanup failed", exception);
            return false;
        }
        return true;
    }
}
