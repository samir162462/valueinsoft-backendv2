package com.example.valueinsoftbackend.notification.repository;

import com.example.valueinsoftbackend.notification.model.NotificationCatalogEntry;
import com.example.valueinsoftbackend.notification.model.NotificationEvent;
import com.example.valueinsoftbackend.notification.model.RenderedNotification;
import com.example.valueinsoftbackend.notification.service.CanonicalJsonService;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

@Repository
public class DbNotificationRecipient {
    private final JdbcTemplate jdbc;
    private final CanonicalJsonService canonicalJson;

    public DbNotificationRecipient(JdbcTemplate jdbc, CanonicalJsonService canonicalJson) {
        this.jdbc = jdbc;
        this.canonicalJson = canonicalJson;
    }

    public Optional<OpenRecipient> lockOpen(long companyId, int userId, String groupKey) {
        if (groupKey == null) {
            return Optional.empty();
        }
        return jdbc.query("""
                        SELECT recipient_id, recipient_uuid, aggregate_count,
                               last_event_at, state, category
                        FROM %s
                        WHERE user_id = ? AND group_key = ?
                          AND archived_at IS NULL AND group_closed_at IS NULL
                        FOR UPDATE
                        """.formatted(TenantSqlIdentifiers.notificationRecipientTable(companyId)),
                        (rs, rowNum) -> new OpenRecipient(
                                rs.getLong("recipient_id"),
                                rs.getObject("recipient_uuid", UUID.class),
                                rs.getInt("aggregate_count"),
                                rs.getTimestamp("last_event_at").toInstant(),
                                rs.getString("state"), rs.getString("category")),
                        userId, groupKey)
                .stream().findFirst();
    }

    public NewRecipient insert(long companyId, int userId, NotificationEvent event,
                               NotificationCatalogEntry catalog,
                               RenderedNotification rendered, long changeSequence) {
        String sql = """
                INSERT INTO %s (
                    user_id, branch_id, type_key, category, group_key,
                    first_event_id, latest_event_id, aggregate_count,
                    rendered_title, rendered_body, rendered_preview, rendered_locale,
                    template_version, render_status, deep_link_snapshot,
                    subject_type, subject_id, params, priority, state, change_sequence,
                    last_event_at, purge_after
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb,
                          ?, 'unseen', ?, CAST(? AS timestamptz),
                          CAST(? AS timestamptz) + make_interval(days => ?))
                RETURNING recipient_id, recipient_uuid
                """.formatted(TenantSqlIdentifiers.notificationRecipientTable(companyId));
        return jdbc.query(sql, (rs, rowNum) ->
                        new NewRecipient(rs.getLong("recipient_id"),
                                rs.getObject("recipient_uuid", UUID.class)),
                userId, event.branchId(), event.typeKey(), catalog.category(),
                rendered.groupKey(), event.eventId(), event.eventId(),
                rendered.title(), rendered.body(), rendered.preview(), rendered.locale(),
                rendered.templateVersion(), rendered.renderStatus(), rendered.deepLink(),
                event.subjectType(), event.subjectId(), canonicalJson.canonicalize(event.params()),
                event.priority(), changeSequence, Timestamp.from(event.createdAt()),
                Timestamp.from(event.createdAt()),
                event.retentionDays()).getFirst();
    }

    public long closeGroup(long companyId, long recipientId) {
        long sequence = nextSequence(companyId);
        jdbc.update("""
                UPDATE %s SET group_closed_at = NOW(), change_sequence = ?
                WHERE recipient_id = ?
                """.formatted(TenantSqlIdentifiers.notificationRecipientTable(companyId)),
                sequence, recipientId);
        return sequence;
    }

    public long aggregate(long companyId, OpenRecipient target, NotificationEvent event,
                          RenderedNotification rendered) {
        long sequence = nextSequence(companyId);
        jdbc.update("""
                WITH incoming AS (
                    SELECT CAST(? AS bigint) AS event_id,
                           CAST(? AS timestamptz) AS event_at,
                           CAST(? AS text) AS title,
                           CAST(? AS text) AS body,
                           CAST(? AS text) AS preview,
                           CAST(? AS text) AS locale,
                           CAST(? AS integer) AS template_version,
                           CAST(? AS text) AS render_status,
                           CAST(? AS text) AS deep_link,
                           CAST(? AS text) AS subject_type,
                           CAST(? AS bigint) AS subject_id,
                           CAST(? AS jsonb) AS params,
                           CAST(? AS text) AS priority,
                           CAST(? AS bigint) AS change_sequence,
                           CAST(? AS integer) AS retention_days
                )
                UPDATE %s recipient
                SET aggregate_count = aggregate_count + 1,
                    latest_event_id = CASE
                      WHEN (incoming.event_at, incoming.event_id) >=
                           (recipient.last_event_at, recipient.latest_event_id)
                      THEN incoming.event_id ELSE recipient.latest_event_id END,
                    rendered_title = CASE
                      WHEN (incoming.event_at, incoming.event_id) >=
                           (recipient.last_event_at, recipient.latest_event_id)
                      THEN incoming.title ELSE recipient.rendered_title END,
                    rendered_body = CASE
                      WHEN (incoming.event_at, incoming.event_id) >=
                           (recipient.last_event_at, recipient.latest_event_id)
                      THEN incoming.body ELSE recipient.rendered_body END,
                    rendered_preview = CASE
                      WHEN (incoming.event_at, incoming.event_id) >=
                           (recipient.last_event_at, recipient.latest_event_id)
                      THEN incoming.preview ELSE recipient.rendered_preview END,
                    rendered_locale = CASE
                      WHEN (incoming.event_at, incoming.event_id) >=
                           (recipient.last_event_at, recipient.latest_event_id)
                      THEN incoming.locale ELSE recipient.rendered_locale END,
                    template_version = CASE
                      WHEN (incoming.event_at, incoming.event_id) >=
                           (recipient.last_event_at, recipient.latest_event_id)
                      THEN incoming.template_version ELSE recipient.template_version END,
                    render_status = CASE
                      WHEN (incoming.event_at, incoming.event_id) >=
                           (recipient.last_event_at, recipient.latest_event_id)
                      THEN incoming.render_status ELSE recipient.render_status END,
                    deep_link_snapshot = CASE
                      WHEN (incoming.event_at, incoming.event_id) >=
                           (recipient.last_event_at, recipient.latest_event_id)
                      THEN incoming.deep_link ELSE recipient.deep_link_snapshot END,
                    subject_type = CASE
                      WHEN (incoming.event_at, incoming.event_id) >=
                           (recipient.last_event_at, recipient.latest_event_id)
                      THEN incoming.subject_type ELSE recipient.subject_type END,
                    subject_id = CASE
                      WHEN (incoming.event_at, incoming.event_id) >=
                           (recipient.last_event_at, recipient.latest_event_id)
                      THEN incoming.subject_id ELSE recipient.subject_id END,
                    params = CASE
                      WHEN (incoming.event_at, incoming.event_id) >=
                           (recipient.last_event_at, recipient.latest_event_id)
                      THEN incoming.params ELSE recipient.params END,
                    priority = CASE
                      WHEN public.notification_priority_rank(incoming.priority) <
                           public.notification_priority_rank(recipient.priority)
                      THEN incoming.priority ELSE recipient.priority END,
                    state = 'unseen', seen_at = NULL, read_at = NULL,
                    last_event_at = GREATEST(recipient.last_event_at, incoming.event_at),
                    change_sequence = incoming.change_sequence,
                    purge_after = GREATEST(
                        recipient.purge_after,
                        incoming.event_at + make_interval(days => incoming.retention_days))
                FROM incoming
                WHERE recipient.recipient_id = ?
                """.formatted(TenantSqlIdentifiers.notificationRecipientTable(companyId)),
                event.eventId(), Timestamp.from(event.createdAt()),
                rendered.title(), rendered.body(), rendered.preview(),
                rendered.locale(), rendered.templateVersion(), rendered.renderStatus(),
                rendered.deepLink(), event.subjectType(), event.subjectId(),
                canonicalJson.canonicalize(event.params()), event.priority(), sequence,
                event.retentionDays(), target.recipientId());
        return sequence;
    }

    public void audit(long companyId, long recipientId, int userId, String category,
                      String fromState, String toState, String channel) {
        jdbc.update("""
                INSERT INTO %s
                    (recipient_id, user_id, category, from_state, to_state, channel)
                VALUES (?, ?, ?, ?, ?, ?)
                """.formatted(TenantSqlIdentifiers.notificationRecipientAuditTable(companyId)),
                recipientId, userId, category, fromState, toState, channel);
    }

    public long findChangeSequence(long companyId, long recipientId) {
        return jdbc.queryForObject("SELECT change_sequence FROM "
                        + TenantSqlIdentifiers.notificationRecipientTable(companyId)
                        + " WHERE recipient_id = ?", Long.class, recipientId);
    }

    private long nextSequence(long companyId) {
        return jdbc.queryForObject("SELECT nextval('" +
                TenantSqlIdentifiers.notificationFeedChangeSequence(companyId) + "')",
                Long.class);
    }

    public record OpenRecipient(
            long recipientId, UUID recipientUuid, int aggregateCount,
            Instant lastEventAt, String state, String category) {}
    public record NewRecipient(long recipientId, UUID recipientUuid) {}
}
