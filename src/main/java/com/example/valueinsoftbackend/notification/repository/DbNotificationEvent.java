package com.example.valueinsoftbackend.notification.repository;

import com.example.valueinsoftbackend.notification.model.NotificationEvent;
import com.example.valueinsoftbackend.notification.model.NotificationRequest;
import com.example.valueinsoftbackend.notification.service.CanonicalJsonService;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class DbNotificationEvent {
    private final JdbcTemplate jdbc;
    private final CanonicalJsonService canonicalJson;
    private final ObjectMapper objectMapper;

    public DbNotificationEvent(JdbcTemplate jdbc,
                               CanonicalJsonService canonicalJson,
                               ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.canonicalJson = canonicalJson;
        this.objectMapper = objectMapper;
    }

    public Optional<Long> insert(NotificationRequest request, byte[] fingerprint) {
        String table = TenantSqlIdentifiers.notificationEventTable(request.companyId());
        String sql = """
                INSERT INTO %s (
                    type_key, idempotency_key, request_fingerprint, branch_id, actor_user_id,
                    subject_type, subject_id, params, priority, group_key, source, broadcast_id,
                    correlation_id, retention_days, expires_at
                )
                SELECT c.type_key, ?, ?, ?, ?, ?, ?, ?::jsonb,
                       COALESCE(?, c.default_priority), ?, ?, ?, ?, c.retention_days,
                       NOW() + make_interval(days => c.retention_days)
                FROM public.notification_type_catalog c
                WHERE c.type_key = ? AND c.status = 'active'
                ON CONFLICT (idempotency_key) DO NOTHING
                RETURNING event_id
                """.formatted(table);
        List<Long> ids = jdbc.query(sql, (rs, rowNum) -> rs.getLong(1),
                request.idempotencyKey(), fingerprint, request.branchId(), request.actorUserId(),
                request.subjectType(), request.subjectId(), canonicalJson.canonicalize(request.params()),
                request.priority(), request.groupKey(), request.source(), request.broadcastId(),
                request.correlationId(), request.typeKey());
        if (ids.isEmpty() && !existsByIdempotencyKey(request.companyId(), request.idempotencyKey())) {
            throw new IllegalArgumentException("Unknown or inactive notification type: " + request.typeKey());
        }
        return ids.stream().findFirst();
    }

    public ExistingEvent requireExisting(long companyId, String idempotencyKey) {
        String sql = "SELECT event_id, request_fingerprint FROM "
                + TenantSqlIdentifiers.notificationEventTable(companyId)
                + " WHERE idempotency_key = ?";
        return jdbc.query(sql,
                        (rs, rowNum) -> new ExistingEvent(rs.getLong(1), rs.getBytes(2)),
                        idempotencyKey)
                .stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Idempotency conflict row disappeared"));
    }

    public NotificationEvent require(long companyId, long eventId) {
        String sql = """
                SELECT event_id, type_key, branch_id, actor_user_id, subject_type, subject_id,
                       params::text, priority, group_key, source, broadcast_id, correlation_id,
                       retention_days, created_at
                FROM %s WHERE event_id = ?
                """.formatted(TenantSqlIdentifiers.notificationEventTable(companyId));
        return jdbc.query(sql, (rs, rowNum) -> mapEvent(rs), eventId).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Notification event not found: " + eventId));
    }

    private boolean existsByIdempotencyKey(long companyId, String key) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM "
                        + TenantSqlIdentifiers.notificationEventTable(companyId)
                        + " WHERE idempotency_key = ?", Integer.class, key);
        return count != null && count > 0;
    }

    private NotificationEvent mapEvent(ResultSet rs) throws SQLException {
        try {
            Map<String, Object> params = objectMapper.readValue(
                    rs.getString("params"), new TypeReference<>() {});
            return new NotificationEvent(
                    rs.getLong("event_id"), rs.getString("type_key"),
                    nullableInt(rs, "branch_id"), nullableInt(rs, "actor_user_id"),
                    rs.getString("subject_type"), nullableLong(rs, "subject_id"), params,
                    rs.getString("priority"), rs.getString("group_key"), rs.getString("source"),
                    nullableLong(rs, "broadcast_id"), rs.getString("correlation_id"),
                    rs.getInt("retention_days"), rs.getTimestamp("created_at").toInstant());
        } catch (Exception ex) {
            throw new SQLException("Invalid notification params JSON", ex);
        }
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    public record ExistingEvent(long eventId, byte[] fingerprint) {}
}
