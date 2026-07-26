package com.example.valueinsoftbackend.notification.repository;

import com.example.valueinsoftbackend.notification.model.NotificationFeedEvent;
import com.example.valueinsoftbackend.notification.model.NotificationFeedItem;
import com.example.valueinsoftbackend.notification.service.NotificationCursorCodec;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class DbNotificationFeed {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public DbNotificationFeed(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public List<NotificationFeedItem> page(long companyId, int userId,
                                           NotificationCursorCodec.Cursor cursor,
                                           String category, Integer branchId,
                                           String state, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT r.*, c.required_capability
                FROM %s r
                JOIN public.notification_type_catalog c ON c.type_key = r.type_key
                WHERE r.user_id = ? AND r.archived_at IS NULL
                """.formatted(TenantSqlIdentifiers.notificationRecipientTable(companyId)));
        List<Object> args = new ArrayList<>();
        args.add(userId);
        if (cursor != null) {
            sql.append(" AND (r.last_event_at, r.recipient_id) < (?, ?)");
            args.add(java.sql.Timestamp.from(cursor.lastEventAt()));
            args.add(cursor.recipientId());
        }
        if (category != null && !category.isBlank()) {
            sql.append(" AND r.category = ?");
            args.add(category);
        }
        if (branchId != null) {
            sql.append(" AND r.branch_id = ?");
            args.add(branchId);
        }
        if (state != null && !state.isBlank()) {
            sql.append(" AND r.state = ?");
            args.add(state);
        }
        sql.append(" ORDER BY r.last_event_at DESC, r.recipient_id DESC LIMIT ?");
        args.add(limit);
        return jdbc.query(sql.toString(), (rs, rowNum) -> mapItem(rs), args.toArray());
    }

    public NotificationFeedItem require(long companyId, int userId, UUID recipientUuid) {
        return jdbc.query("""
                        SELECT r.*, c.required_capability
                        FROM %s r
                        JOIN public.notification_type_catalog c ON c.type_key = r.type_key
                        WHERE r.user_id = ? AND r.recipient_uuid = ?
                        """.formatted(TenantSqlIdentifiers.notificationRecipientTable(companyId)),
                        (rs, rowNum) -> mapItem(rs), userId, recipientUuid)
                .stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));
    }

    public List<NotificationFeedEvent> lineage(long companyId, int userId, UUID recipientUuid) {
        return jdbc.query("""
                SELECT e.event_id, l.sequence_no, e.type_key, e.params::text,
                       e.actor_user_id, e.subject_type, e.subject_id, l.contributed_at
                FROM %s l
                JOIN %s r ON r.recipient_id = l.recipient_id
                JOIN %s e ON e.event_id = l.event_id
                WHERE r.user_id = ? AND r.recipient_uuid = ?
                ORDER BY l.sequence_no
                """.formatted(
                        TenantSqlIdentifiers.notificationRecipientEventTable(companyId),
                        TenantSqlIdentifiers.notificationRecipientTable(companyId),
                        TenantSqlIdentifiers.notificationEventTable(companyId)),
                (rs, rowNum) -> new NotificationFeedEvent(
                        rs.getLong("event_id"), rs.getInt("sequence_no"),
                        rs.getString("type_key"), parseParams(rs.getString("params")),
                        nullableInt(rs, "actor_user_id"), rs.getString("subject_type"),
                        nullableLong(rs, "subject_id"),
                        rs.getTimestamp("contributed_at").toInstant()),
                userId, recipientUuid);
    }

    public List<SummaryRow> summaryRows(long companyId, int userId) {
        return jdbc.query("""
                SELECT r.state, r.last_event_at, r.change_sequence, r.branch_id, r.type_key,
                       c.required_capability
                FROM %s r
                JOIN public.notification_type_catalog c ON c.type_key = r.type_key
                WHERE r.user_id = ? AND r.archived_at IS NULL
                """.formatted(TenantSqlIdentifiers.notificationRecipientTable(companyId)),
                (rs, rowNum) -> new SummaryRow(
                        rs.getString("state"), rs.getTimestamp("last_event_at").toInstant(),
                        rs.getLong("change_sequence"), nullableInt(rs, "branch_id"),
                        rs.getString("type_key"),
                        rs.getString("required_capability")),
                userId);
    }

    /**
     * SSE replay (NC-6.6, ADR-11).
     *
     * <p>Driven by the append-only change log, <strong>not</strong> by the recipient table.
     * That is the whole point of the log: aggregation <em>updates</em> an existing recipient
     * row without minting a new id, so a client that replayed on {@code recipient_id} would
     * never learn that an item it already holds had changed — the exact case aggregation is
     * built to produce.
     *
     * <p>The join returns the recipient's state <em>at query time</em>, so three changes to
     * one item replay as three events all carrying its current content. The client upserts by
     * {@code recipientUuid}, so that converges correctly and is cheaper than deduplicating
     * here.
     */
    public List<NotificationFeedItem> replaySince(long companyId, int userId,
                                                  long sinceChangeSequence, int limit) {
        return jdbc.query("""
                        SELECT r.*, c.required_capability
                        FROM %s ch
                        JOIN %s r ON r.recipient_id = ch.recipient_id
                        JOIN public.notification_type_catalog c ON c.type_key = r.type_key
                        WHERE ch.user_id = ? AND ch.change_sequence > ?
                        ORDER BY ch.change_sequence
                        LIMIT ?
                        """.formatted(
                                TenantSqlIdentifiers.notificationFeedChangeTable(companyId),
                                TenantSqlIdentifiers.notificationRecipientTable(companyId)),
                (rs, rowNum) -> mapItem(rs), userId, sinceChangeSequence, limit);
    }

    /**
     * Oldest change still retained for this user. A {@code Last-Event-ID} below this means the
     * client slept through the retention window and cannot be caught up incrementally — the
     * stream answers with a single {@code reset} instead of silently skipping changes.
     * Returns 0 when the log is empty, which correctly means "nothing to reset for".
     */
    public long minRetainedChangeSequence(long companyId, int userId) {
        Long value = jdbc.queryForObject("""
                SELECT COALESCE(MIN(change_sequence), 0)
                FROM %s
                WHERE user_id = ?
                """.formatted(TenantSqlIdentifiers.notificationFeedChangeTable(companyId)),
                Long.class, userId);
        return value == null ? 0L : value;
    }

    /** Count of pending changes, used to decide reset-versus-replay without fetching rows. */
    public long pendingChangeCount(long companyId, int userId, long sinceChangeSequence) {
        Long value = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM %s
                WHERE user_id = ? AND change_sequence > ?
                """.formatted(TenantSqlIdentifiers.notificationFeedChangeTable(companyId)),
                Long.class, userId, sinceChangeSequence);
        return value == null ? 0L : value;
    }

    public LockedRecipient lock(long companyId, int userId, UUID uuid) {
        return jdbc.query("""
                        SELECT recipient_id, category, state
                        FROM %s WHERE user_id = ? AND recipient_uuid = ? FOR UPDATE
                        """.formatted(TenantSqlIdentifiers.notificationRecipientTable(companyId)),
                        (rs, rowNum) -> new LockedRecipient(
                                rs.getLong("recipient_id"), rs.getString("category"),
                                rs.getString("state")),
                        userId, uuid)
                .stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));
    }

    public long locklessRecipientId(long companyId, int userId, UUID uuid) {
        return jdbc.queryForObject("SELECT recipient_id FROM "
                        + TenantSqlIdentifiers.notificationRecipientTable(companyId)
                        + " WHERE user_id = ? AND recipient_uuid = ?",
                Long.class, userId, uuid);
    }

    public List<LockedRecipient> lockUnread(long companyId, int userId) {
        return jdbc.query("""
                SELECT recipient_id, category, state
                FROM %s
                WHERE user_id = ? AND state IN ('unseen','seen')
                ORDER BY recipient_id FOR UPDATE
                """.formatted(TenantSqlIdentifiers.notificationRecipientTable(companyId)),
                (rs, rowNum) -> new LockedRecipient(
                        rs.getLong("recipient_id"), rs.getString("category"),
                        rs.getString("state")), userId);
    }

    public void markSeen(long companyId, long recipientId, long sequence) {
        jdbc.update("""
                UPDATE %s SET state='seen', seen_at=COALESCE(seen_at, NOW()),
                    change_sequence=? WHERE recipient_id=? AND state='unseen'
                """.formatted(TenantSqlIdentifiers.notificationRecipientTable(companyId)),
                sequence, recipientId);
    }

    public void markRead(long companyId, long recipientId, long sequence) {
        jdbc.update("""
                UPDATE %s SET state='read', seen_at=COALESCE(seen_at, NOW()),
                    read_at=COALESCE(read_at, NOW()), change_sequence=?
                WHERE recipient_id=? AND state IN ('unseen','seen')
                """.formatted(TenantSqlIdentifiers.notificationRecipientTable(companyId)),
                sequence, recipientId);
    }

    public void markClicked(long companyId, long recipientId, long sequence) {
        jdbc.update("""
                UPDATE %s SET clicked_at=COALESCE(clicked_at, NOW()), change_sequence=?
                WHERE recipient_id=?
                """.formatted(TenantSqlIdentifiers.notificationRecipientTable(companyId)),
                sequence, recipientId);
    }

    public void archive(long companyId, long recipientId, long sequence) {
        jdbc.update("""
                UPDATE %s SET state='archived', archived_at=COALESCE(archived_at, NOW()),
                    group_closed_at=CASE WHEN group_key IS NULL THEN group_closed_at
                                         ELSE COALESCE(group_closed_at, NOW()) END,
                    change_sequence=? WHERE recipient_id=? AND state <> 'archived'
                """.formatted(TenantSqlIdentifiers.notificationRecipientTable(companyId)),
                sequence, recipientId);
    }

    private NotificationFeedItem mapItem(ResultSet rs) throws SQLException {
        return new NotificationFeedItem(
                rs.getObject("recipient_uuid", UUID.class), rs.getLong("change_sequence"),
                rs.getString("type_key"), rs.getString("category"), rs.getString("priority"),
                rs.getString("rendered_title"), rs.getString("rendered_body"),
                rs.getString("rendered_locale"), nullableInt(rs, "template_version"),
                rs.getString("render_status"), rs.getString("deep_link_snapshot"),
                rs.getString("subject_type"), nullableLong(rs, "subject_id"),
                parseParams(rs.getString("params")), rs.getInt("aggregate_count"),
                rs.getString("state"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("last_event_at").toInstant(), nullableInt(rs, "branch_id"),
                rs.getString("required_capability"));
    }

    private Map<String, Object> parseParams(String json) throws SQLException {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
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

    public record SummaryRow(String state, Instant lastEventAt, long changeSequence,
                             Integer branchId, String typeKey, String requiredCapability) {}
    public record LockedRecipient(long recipientId, String category, String state) {}
}
