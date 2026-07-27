package com.example.valueinsoftbackend.notification.repository;

import com.example.valueinsoftbackend.notification.control.ControlComponent;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public class DbNotificationControl {
    private final NamedParameterJdbcTemplate jdbc;

    public DbNotificationControl(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ControlState change(ControlComponent component,
                               boolean enabled,
                               String suppressionMode,
                               String reason,
                               OffsetDateTime disabledUntil,
                               int actorUserId,
                               String actorIp,
                               Integer queueDepth) {
        MapSqlParameterSource key = new MapSqlParameterSource()
                .addValue("scope", component.scope())
                .addValue("componentKey", component.key());
        List<PreviousState> previousRows = jdbc.query(
                """
                SELECT enabled, suppression_mode
                FROM public.notification_control_switch
                WHERE scope = :scope AND component_key = :componentKey
                FOR UPDATE
                """,
                key,
                (rs, rowNum) -> new PreviousState(
                        rs.getBoolean("enabled"),
                        rs.getString("suppression_mode")));
        PreviousState previous = previousRows.isEmpty() ? null : previousRows.get(0);

        Long version = jdbc.queryForObject(
                "SELECT nextval('public.notification_control_version_seq')",
                new MapSqlParameterSource(),
                Long.class);
        if (version == null) {
            throw new IllegalStateException("Notification control version was not generated");
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("scope", component.scope())
                .addValue("componentKey", component.key())
                .addValue("enabled", enabled)
                .addValue("suppressionMode", suppressionMode)
                .addValue("reason", reason)
                .addValue("disabledUntil", disabledUntil)
                .addValue("actorUserId", actorUserId)
                .addValue("actorIp", actorIp)
                .addValue("controlVersion", version)
                .addValue("queueDepth", queueDepth)
                .addValue("fromEnabled", previous == null ? null : previous.enabled())
                .addValue("fromSuppression", previous == null ? null : previous.suppressionMode());

        jdbc.update(
                """
                INSERT INTO public.notification_control_switch (
                    scope, component_key, enabled, suppression_mode, reason,
                    disabled_until, changed_by_user_id, changed_at, control_version
                ) VALUES (
                    :scope, :componentKey, :enabled, :suppressionMode, :reason,
                    :disabledUntil, :actorUserId, NOW(), :controlVersion
                )
                ON CONFLICT (scope, component_key) DO UPDATE SET
                    enabled = EXCLUDED.enabled,
                    suppression_mode = EXCLUDED.suppression_mode,
                    reason = EXCLUDED.reason,
                    disabled_until = EXCLUDED.disabled_until,
                    changed_by_user_id = EXCLUDED.changed_by_user_id,
                    changed_at = EXCLUDED.changed_at,
                    control_version = EXCLUDED.control_version
                """,
                params);
        jdbc.update(
                """
                INSERT INTO public.notification_control_audit (
                    scope, component_key, from_enabled, to_enabled,
                    from_suppression, to_suppression, reason, disabled_until,
                    actor_user_id, actor_ip, control_version, queue_depth_at_change
                ) VALUES (
                    :scope, :componentKey, :fromEnabled, :enabled,
                    :fromSuppression, :suppressionMode, :reason, :disabledUntil,
                    :actorUserId, CAST(:actorIp AS inet), :controlVersion, :queueDepth
                )
                """,
                params);
        return new ControlState(
                component.scope(),
                component.key(),
                enabled,
                suppressionMode,
                reason,
                disabledUntil,
                actorUserId,
                version);
    }

    public List<ControlState> findAll() {
        return jdbc.query(
                """
                SELECT scope, component_key, enabled, suppression_mode, reason,
                       disabled_until, changed_by_user_id, control_version
                FROM public.notification_control_switch
                ORDER BY scope, component_key
                """,
                new MapSqlParameterSource(),
                (rs, rowNum) -> new ControlState(
                        rs.getString("scope"),
                        rs.getString("component_key"),
                        rs.getBoolean("enabled"),
                        rs.getString("suppression_mode"),
                        rs.getString("reason"),
                        rs.getObject("disabled_until", OffsetDateTime.class),
                        rs.getInt("changed_by_user_id"),
                        rs.getLong("control_version")));
    }

    public List<ControlAudit> findAudit(int limit) {
        return jdbc.query(
                """
                SELECT audit_id, scope, component_key, from_enabled, to_enabled,
                       from_suppression, to_suppression, reason, disabled_until,
                       actor_user_id, actor_ip::text AS actor_ip, control_version,
                       queue_depth_at_change, occurred_at
                FROM public.notification_control_audit
                ORDER BY occurred_at DESC, audit_id DESC
                LIMIT :limit
                """,
                new MapSqlParameterSource("limit", Math.max(1, Math.min(limit, 500))),
                (rs, rowNum) -> new ControlAudit(
                        rs.getLong("audit_id"),
                        rs.getString("scope"),
                        rs.getString("component_key"),
                        (Boolean) rs.getObject("from_enabled"),
                        rs.getBoolean("to_enabled"),
                        rs.getString("from_suppression"),
                        rs.getString("to_suppression"),
                        rs.getString("reason"),
                        rs.getObject("disabled_until", OffsetDateTime.class),
                        rs.getInt("actor_user_id"),
                        rs.getString("actor_ip"),
                        rs.getLong("control_version"),
                        (Integer) rs.getObject("queue_depth_at_change"),
                        rs.getObject("occurred_at", OffsetDateTime.class)));
    }

    private record PreviousState(boolean enabled, String suppressionMode) {
    }

    public record ControlState(String scope,
                               String componentKey,
                               boolean enabled,
                               String suppressionMode,
                               String reason,
                               OffsetDateTime disabledUntil,
                               int changedByUserId,
                               long controlVersion) {
    }

    public record ControlAudit(long auditId,
                               String scope,
                               String componentKey,
                               Boolean fromEnabled,
                               boolean toEnabled,
                               String fromSuppression,
                               String toSuppression,
                               String reason,
                               OffsetDateTime disabledUntil,
                               int actorUserId,
                               String actorIp,
                               long controlVersion,
                               Integer queueDepthAtChange,
                               OffsetDateTime occurredAt) {
    }
}
