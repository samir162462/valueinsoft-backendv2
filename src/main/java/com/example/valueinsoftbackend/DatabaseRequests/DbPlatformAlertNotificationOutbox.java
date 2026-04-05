package com.example.valueinsoftbackend.DatabaseRequests;

import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformAlertNotificationOutboxItem;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformAlertNotificationOutboxPageResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;

@Repository
public class DbPlatformAlertNotificationOutbox {

    private static final RowMapper<PlatformAlertNotificationOutboxItem> OUTBOX_ROW_MAPPER = (rs, rowNum) ->
            new PlatformAlertNotificationOutboxItem(
                    rs.getLong("notification_id"),
                    rs.getString("alert_key"),
                    (Integer) rs.getObject("target_tenant_id"),
                    (Integer) rs.getObject("target_branch_id"),
                    rs.getString("event_type"),
                    rs.getString("payload_json"),
                    rs.getString("status"),
                    rs.getInt("attempt_count"),
                    (Integer) rs.getObject("requested_by_user_id"),
                    rs.getString("requested_by_user_name"),
                    rs.getTimestamp("created_at"),
                    rs.getTimestamp("processed_at"),
                    rs.getString("last_error")
            );

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public DbPlatformAlertNotificationOutbox(JdbcTemplate jdbcTemplate,
                                             NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public long createOutboxEvent(String alertKey,
                                  Integer targetTenantId,
                                  Integer targetBranchId,
                                  String eventType,
                                  String payloadJson,
                                  Integer requestedByUserId,
                                  String requestedByUserName) {
        Long id = jdbcTemplate.queryForObject(
                "INSERT INTO public.platform_alert_notification_outbox " +
                        "(alert_key, target_tenant_id, target_branch_id, event_type, payload, requested_by_user_id, requested_by_user_name) " +
                        "VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?, ?) RETURNING notification_id",
                Long.class,
                alertKey,
                targetTenantId,
                targetBranchId,
                eventType,
                payloadJson == null ? "{}" : payloadJson,
                requestedByUserId,
                requestedByUserName
        );
        return id == null ? 0 : id;
    }

    public PlatformAlertNotificationOutboxPageResponse getOutboxEvents(String alertKey,
                                                                       String eventType,
                                                                       String status,
                                                                       Integer tenantId,
                                                                       Integer branchId,
                                                                       int page,
                                                                       int size) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        int offset = (normalizedPage - 1) * normalizedSize;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", normalizedSize)
                .addValue("offset", offset);

        String whereClause = buildWhereClause(alertKey, eventType, status, tenantId, branchId, params);
        String baseSql = "FROM public.platform_alert_notification_outbox " + whereClause;

        Long totalItemsValue = namedParameterJdbcTemplate.queryForObject(
                "SELECT COUNT(*) " + baseSql,
                params,
                Long.class
        );
        long totalItems = totalItemsValue == null ? 0 : totalItemsValue;

        ArrayList<PlatformAlertNotificationOutboxItem> items = new ArrayList<>(
                namedParameterJdbcTemplate.query(
                        "SELECT notification_id, alert_key, target_tenant_id, target_branch_id, event_type, " +
                                "payload::text AS payload_json, status, attempt_count, requested_by_user_id, " +
                                "requested_by_user_name, created_at, processed_at, last_error " +
                                baseSql +
                                " ORDER BY created_at DESC, notification_id DESC LIMIT :limit OFFSET :offset",
                        params,
                        OUTBOX_ROW_MAPPER
                )
        );

        int totalPages = totalItems == 0 ? 0 : (int) Math.ceil((double) totalItems / normalizedSize);
        return new PlatformAlertNotificationOutboxPageResponse(items, normalizedPage, normalizedSize, totalItems, totalPages);
    }

    private String buildWhereClause(String alertKey,
                                    String eventType,
                                    String status,
                                    Integer tenantId,
                                    Integer branchId,
                                    MapSqlParameterSource params) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1 ");
        if (alertKey != null && !alertKey.trim().isEmpty()) {
            params.addValue("alertKey", alertKey.trim().toLowerCase());
            where.append(" AND alert_key = :alertKey ");
        }
        if (eventType != null && !eventType.trim().isEmpty()) {
            params.addValue("eventType", eventType.trim().toLowerCase());
            where.append(" AND LOWER(event_type) = :eventType ");
        }
        if (status != null && !status.trim().isEmpty()) {
            params.addValue("status", status.trim().toLowerCase());
            where.append(" AND LOWER(status) = :status ");
        }
        if (tenantId != null) {
            params.addValue("tenantId", tenantId);
            where.append(" AND target_tenant_id = :tenantId ");
        }
        if (branchId != null) {
            params.addValue("branchId", branchId);
            where.append(" AND target_branch_id = :branchId ");
        }
        return where.toString();
    }
}
