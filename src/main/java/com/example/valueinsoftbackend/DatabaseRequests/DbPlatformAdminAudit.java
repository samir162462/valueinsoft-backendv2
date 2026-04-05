package com.example.valueinsoftbackend.DatabaseRequests;

import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformAuditEventItem;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformAuditEventsPageResponse;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class DbPlatformAdminAudit {

    private static final RowMapper<PlatformAuditEventItem> AUDIT_EVENT_ROW_MAPPER = (rs, rowNum) ->
            new PlatformAuditEventItem(
                    rs.getLong("event_id"),
                    (Integer) rs.getObject("actor_user_id"),
                    rs.getString("actor_user_name"),
                    rs.getString("capability_key"),
                    rs.getString("action_type"),
                    (Integer) rs.getObject("target_tenant_id"),
                    (Integer) rs.getObject("target_branch_id"),
                    rs.getString("request_summary_json"),
                    rs.getString("context_summary_json"),
                    rs.getString("result_status"),
                    rs.getString("correlation_id"),
                    rs.getTimestamp("created_at")
            );

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public DbPlatformAdminAudit(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public PlatformAuditEventsPageResponse getAuditEvents(Integer targetTenantId,
                                                          Integer targetBranchId,
                                                          String actorUserName,
                                                          String actionType,
                                                          String resultStatus,
                                                          int page,
                                                          int size) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        int offset = (normalizedPage - 1) * normalizedSize;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", normalizedSize)
                .addValue("offset", offset);

        String whereClause = buildWhereClause(targetTenantId, targetBranchId, actorUserName, actionType, resultStatus, params);
        String baseSql = "FROM public.platform_admin_audit_log pal " + whereClause;

        Long totalItemsValue = namedParameterJdbcTemplate.queryForObject(
                "SELECT COUNT(*) " + baseSql,
                params,
                Long.class
        );
        long totalItems = totalItemsValue == null ? 0 : totalItemsValue;

        String listSql = "SELECT pal.event_id, pal.actor_user_id, pal.actor_user_name, pal.capability_key, pal.action_type, " +
                "pal.target_tenant_id, pal.target_branch_id, pal.request_summary::text AS request_summary_json, " +
                "pal.context_summary::text AS context_summary_json, pal.result_status, pal.correlation_id, pal.created_at " +
                baseSql +
                " ORDER BY pal.created_at DESC, pal.event_id DESC LIMIT :limit OFFSET :offset";

        ArrayList<PlatformAuditEventItem> items = new ArrayList<>(
                namedParameterJdbcTemplate.query(listSql, params, AUDIT_EVENT_ROW_MAPPER)
        );

        int totalPages = totalItems == 0 ? 0 : (int) Math.ceil((double) totalItems / normalizedSize);
        return new PlatformAuditEventsPageResponse(items, normalizedPage, normalizedSize, totalItems, totalPages);
    }

    public PlatformAuditEventItem getLatestMetricsRefreshEvent() {
        return getLatestAuditEventByActionTypes(
                List.of("platform.metrics.refresh_daily", "platform.metrics.refresh_daily.scheduled"),
                null
        );
    }

    public PlatformAuditEventItem getLatestSuccessfulMetricsRefreshEvent() {
        return getLatestAuditEventByActionTypes(
                List.of("platform.metrics.refresh_daily", "platform.metrics.refresh_daily.scheduled"),
                "success"
        );
    }

    public ArrayList<PlatformAuditEventItem> getRecentAuditEvents(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 20);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", safeLimit);

        String sql = "SELECT pal.event_id, pal.actor_user_id, pal.actor_user_name, pal.capability_key, pal.action_type, " +
                "pal.target_tenant_id, pal.target_branch_id, pal.request_summary::text AS request_summary_json, " +
                "pal.context_summary::text AS context_summary_json, pal.result_status, pal.correlation_id, pal.created_at " +
                "FROM public.platform_admin_audit_log pal " +
                "ORDER BY pal.created_at DESC, pal.event_id DESC LIMIT :limit";

        return new ArrayList<>(namedParameterJdbcTemplate.query(sql, params, AUDIT_EVENT_ROW_MAPPER));
    }

    private PlatformAuditEventItem getLatestAuditEventByActionTypes(List<String> actionTypes, String resultStatus) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("actionTypes", actionTypes)
                .addValue("resultStatus", resultStatus);

        String sql = "SELECT pal.event_id, pal.actor_user_id, pal.actor_user_name, pal.capability_key, pal.action_type, " +
                "pal.target_tenant_id, pal.target_branch_id, pal.request_summary::text AS request_summary_json, " +
                "pal.context_summary::text AS context_summary_json, pal.result_status, pal.correlation_id, pal.created_at " +
                "FROM public.platform_admin_audit_log pal " +
                "WHERE pal.action_type IN (:actionTypes) " +
                "AND (:resultStatus IS NULL OR LOWER(pal.result_status) = LOWER(:resultStatus)) " +
                "ORDER BY pal.created_at DESC, pal.event_id DESC LIMIT 1";

        List<PlatformAuditEventItem> results = namedParameterJdbcTemplate.query(sql, params, AUDIT_EVENT_ROW_MAPPER);
        return results.isEmpty() ? null : results.get(0);
    }

    private String buildWhereClause(Integer targetTenantId,
                                    Integer targetBranchId,
                                    String actorUserName,
                                    String actionType,
                                    String resultStatus,
                                    MapSqlParameterSource params) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1 ");

        if (targetTenantId != null) {
            params.addValue("targetTenantId", targetTenantId);
            where.append(" AND pal.target_tenant_id = :targetTenantId ");
        }

        if (targetBranchId != null) {
            params.addValue("targetBranchId", targetBranchId);
            where.append(" AND pal.target_branch_id = :targetBranchId ");
        }

        if (actorUserName != null && !actorUserName.trim().isEmpty()) {
            params.addValue("actorUserName", "%" + actorUserName.trim().toLowerCase() + "%");
            where.append(" AND LOWER(pal.actor_user_name) LIKE :actorUserName ");
        }

        if (actionType != null && !actionType.trim().isEmpty()) {
            params.addValue("actionType", actionType.trim().toLowerCase());
            where.append(" AND LOWER(pal.action_type) = :actionType ");
        }

        if (resultStatus != null && !resultStatus.trim().isEmpty()) {
            params.addValue("resultStatus", resultStatus.trim().toLowerCase());
            where.append(" AND LOWER(pal.result_status) = :resultStatus ");
        }

        return where.toString();
    }
}
