package com.example.valueinsoftbackend.notification.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public class DbNotificationShutdownSchedule {
    private static final String COLUMNS = """
            schedule_uuid, name, enabled, quiet_start, quiet_end, timezone,
            days_of_week, effective_from, effective_until, reason,
            created_by_user_id, created_at, updated_by_user_id, updated_at
            """;
    static final String INSERT_SQL = """
            INSERT INTO public.notification_shutdown_schedule (
                name, enabled, quiet_start, quiet_end, timezone, days_of_week,
                effective_from, effective_until, reason,
                created_by_user_id, updated_by_user_id
            ) VALUES (
                :name, :enabled, :quietStart, :quietEnd, :timezone,
                CAST(:daysOfWeek AS SMALLINT[]), :effectiveFrom, :effectiveUntil,
                :reason, :actorUserId, :actorUserId
            )
            RETURNING
            """ + COLUMNS;
    static final String UPDATE_SQL = """
            UPDATE public.notification_shutdown_schedule SET
                name = :name,
                enabled = :enabled,
                quiet_start = :quietStart,
                quiet_end = :quietEnd,
                timezone = :timezone,
                days_of_week = CAST(:daysOfWeek AS SMALLINT[]),
                effective_from = :effectiveFrom,
                effective_until = :effectiveUntil,
                reason = :reason,
                updated_by_user_id = :actorUserId,
                updated_at = NOW()
            WHERE schedule_uuid = :scheduleUuid
            RETURNING
            """ + COLUMNS;

    private final NamedParameterJdbcTemplate jdbc;

    public DbNotificationShutdownSchedule(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ShutdownSchedule> findAll() {
        return jdbc.query(
                "SELECT " + COLUMNS + """
                        FROM public.notification_shutdown_schedule
                        ORDER BY enabled DESC, name, schedule_uuid
                        """,
                new MapSqlParameterSource(),
                (rs, rowNum) -> new ShutdownSchedule(
                        rs.getObject("schedule_uuid", UUID.class),
                        rs.getString("name"),
                        rs.getBoolean("enabled"),
                        rs.getObject("quiet_start", LocalTime.class),
                        rs.getObject("quiet_end", LocalTime.class),
                        rs.getString("timezone"),
                        readDays(rs.getArray("days_of_week")),
                        rs.getObject("effective_from", LocalDate.class),
                        rs.getObject("effective_until", LocalDate.class),
                        rs.getString("reason"),
                        rs.getInt("created_by_user_id"),
                        rs.getObject("created_at", OffsetDateTime.class),
                        rs.getInt("updated_by_user_id"),
                        rs.getObject("updated_at", OffsetDateTime.class)));
    }

    public ShutdownSchedule create(ScheduleChange change, int actorUserId) {
        return jdbc.queryForObject(
                INSERT_SQL,
                parameters(change, actorUserId),
                (rs, rowNum) -> mapReturned(rs));
    }

    public ShutdownSchedule update(UUID scheduleUuid, ScheduleChange change, int actorUserId) {
        MapSqlParameterSource params = parameters(change, actorUserId)
                .addValue("scheduleUuid", scheduleUuid);
        List<ShutdownSchedule> rows = jdbc.query(
                UPDATE_SQL,
                params,
                (rs, rowNum) -> mapReturned(rs));
        return rows.isEmpty() ? null : rows.get(0);
    }

    public boolean delete(UUID scheduleUuid) {
        return jdbc.update(
                """
                DELETE FROM public.notification_shutdown_schedule
                WHERE schedule_uuid = :scheduleUuid
                """,
                new MapSqlParameterSource("scheduleUuid", scheduleUuid)) == 1;
    }

    private static MapSqlParameterSource parameters(ScheduleChange change, int actorUserId) {
        return new MapSqlParameterSource()
                .addValue("name", change.name())
                .addValue("enabled", change.enabled())
                .addValue("quietStart", change.quietStart())
                .addValue("quietEnd", change.quietEnd())
                .addValue("timezone", change.timezone())
                .addValue("daysOfWeek", arrayLiteral(change.daysOfWeek()))
                .addValue("effectiveFrom", change.effectiveFrom())
                .addValue("effectiveUntil", change.effectiveUntil())
                .addValue("reason", change.reason())
                .addValue("actorUserId", actorUserId);
    }

    private static ShutdownSchedule mapReturned(java.sql.ResultSet rs) throws SQLException {
        return new ShutdownSchedule(
                rs.getObject("schedule_uuid", UUID.class),
                rs.getString("name"),
                rs.getBoolean("enabled"),
                rs.getObject("quiet_start", LocalTime.class),
                rs.getObject("quiet_end", LocalTime.class),
                rs.getString("timezone"),
                readDays(rs.getArray("days_of_week")),
                rs.getObject("effective_from", LocalDate.class),
                rs.getObject("effective_until", LocalDate.class),
                rs.getString("reason"),
                rs.getInt("created_by_user_id"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getInt("updated_by_user_id"),
                rs.getObject("updated_at", OffsetDateTime.class));
    }

    private static Set<Integer> readDays(Array sqlArray) throws SQLException {
        if (sqlArray == null) {
            return Set.of();
        }
        Object raw = sqlArray.getArray();
        LinkedHashSet<Integer> days = new LinkedHashSet<>();
        if (raw instanceof Object[] values) {
            Arrays.stream(values)
                    .map(Number.class::cast)
                    .map(Number::intValue)
                    .forEach(days::add);
        }
        return Set.copyOf(days);
    }

    private static String arrayLiteral(Set<Integer> days) {
        return "{" + days.stream()
                .sorted()
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(",")) + "}";
    }

    public record ScheduleChange(
            String name,
            boolean enabled,
            LocalTime quietStart,
            LocalTime quietEnd,
            String timezone,
            Set<Integer> daysOfWeek,
            LocalDate effectiveFrom,
            LocalDate effectiveUntil,
            String reason) {
    }

    public record ShutdownSchedule(
            UUID scheduleUuid,
            String name,
            boolean enabled,
            LocalTime quietStart,
            LocalTime quietEnd,
            String timezone,
            Set<Integer> daysOfWeek,
            LocalDate effectiveFrom,
            LocalDate effectiveUntil,
            String reason,
            int createdByUserId,
            OffsetDateTime createdAt,
            int updatedByUserId,
            OffsetDateTime updatedAt) {
    }
}
