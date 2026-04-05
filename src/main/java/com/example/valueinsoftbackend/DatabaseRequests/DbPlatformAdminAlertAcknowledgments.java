package com.example.valueinsoftbackend.DatabaseRequests;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformAlertAcknowledgmentsPageResponse;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformAlertAcknowledgmentItem;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Repository
public class DbPlatformAdminAlertAcknowledgments {

    private static final RowMapper<PlatformAlertAcknowledgmentItem> ACK_ROW_MAPPER = (rs, rowNum) ->
            new PlatformAlertAcknowledgmentItem(
                    rs.getLong("acknowledgment_id"),
                    rs.getString("alert_key"),
                    (Integer) rs.getObject("target_tenant_id"),
                    (Integer) rs.getObject("target_branch_id"),
                    rs.getString("note"),
                    (Integer) rs.getObject("acknowledged_by_user_id"),
                    rs.getString("acknowledged_by_user_name"),
                    rs.getTimestamp("acknowledged_at"),
                    rs.getTimestamp("expires_at"),
                    rs.getTimestamp("cleared_at"),
                    (Integer) rs.getObject("cleared_by_user_id"),
                    rs.getString("cleared_by_user_name"),
                    rs.getBoolean("active")
            );

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public DbPlatformAdminAlertAcknowledgments(JdbcTemplate jdbcTemplate,
                                               NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public PlatformAlertAcknowledgmentItem createAcknowledgment(String alertKey,
                                                               Integer targetTenantId,
                                                               Integer targetBranchId,
                                                               String note,
                                                               Integer acknowledgedByUserId,
                                                               String acknowledgedByUserName,
                                                               Timestamp expiresAt) {
        Long id = jdbcTemplate.queryForObject(
                "INSERT INTO public.platform_admin_alert_acknowledgments " +
                        "(alert_key, target_tenant_id, target_branch_id, note, acknowledged_by_user_id, acknowledged_by_user_name, expires_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING acknowledgment_id",
                Long.class,
                alertKey,
                targetTenantId,
                targetBranchId,
                note,
                acknowledgedByUserId,
                acknowledgedByUserName,
                expiresAt
        );
        if (id == null) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "PLATFORM_ALERT_ACK_CREATE_FAILED",
                    "Could not create platform alert acknowledgment"
            );
        }
        return requireAcknowledgment(id);
    }

    public PlatformAlertAcknowledgmentItem clearLatestActiveAcknowledgment(String alertKey,
                                                                           Integer targetTenantId,
                                                                           Integer targetBranchId,
                                                                           Integer clearedByUserId,
                                                                           String clearedByUserName) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("alertKey", alertKey)
                .addValue("clearedByUserId", clearedByUserId)
                .addValue("clearedByUserName", clearedByUserName);
        String scopeClause = buildExactScopeClause(targetTenantId, targetBranchId, params);

        Long id = namedParameterJdbcTemplate.queryForObject(
                "WITH latest_active AS (" +
                        " SELECT acknowledgment_id FROM public.platform_admin_alert_acknowledgments " +
                        " WHERE alert_key = :alertKey AND cleared_at IS NULL AND (expires_at IS NULL OR expires_at > NOW()) " +
                        scopeClause +
                        " ORDER BY acknowledged_at DESC, acknowledgment_id DESC LIMIT 1" +
                        ") " +
                        "UPDATE public.platform_admin_alert_acknowledgments paaa " +
                        "SET cleared_at = NOW(), cleared_by_user_id = :clearedByUserId, cleared_by_user_name = :clearedByUserName " +
                        "FROM latest_active la " +
                        "WHERE paaa.acknowledgment_id = la.acknowledgment_id " +
                        "RETURNING paaa.acknowledgment_id",
                params,
                Long.class
        );
        if (id == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "PLATFORM_ALERT_ACK_NOT_FOUND",
                    "No active acknowledgment found for alert: " + alertKey
            );
        }
        return requireAcknowledgment(id);
    }

    public PlatformAlertAcknowledgmentsPageResponse getAcknowledgments(String alertKey,
                                                                       Integer targetTenantId,
                                                                       Integer targetBranchId,
                                                                       Boolean activeOnly,
                                                                       int page,
                                                                       int size) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        int offset = (normalizedPage - 1) * normalizedSize;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", normalizedSize)
                .addValue("offset", offset);
        String whereClause = buildHistoryWhereClause(alertKey, targetTenantId, targetBranchId, activeOnly, params);
        String baseSql = "FROM public.platform_admin_alert_acknowledgments " + whereClause;

        Long totalItemsValue = namedParameterJdbcTemplate.queryForObject(
                "SELECT COUNT(*) " + baseSql,
                params,
                Long.class
        );
        long totalItems = totalItemsValue == null ? 0 : totalItemsValue;

        ArrayList<PlatformAlertAcknowledgmentItem> items = new ArrayList<>(
                namedParameterJdbcTemplate.query(
                        "SELECT acknowledgment_id, alert_key, target_tenant_id, target_branch_id, note, " +
                                "acknowledged_by_user_id, acknowledged_by_user_name, acknowledged_at, expires_at, " +
                                "cleared_at, cleared_by_user_id, cleared_by_user_name, " +
                                "(cleared_at IS NULL AND (expires_at IS NULL OR expires_at > NOW())) AS active " +
                                baseSql +
                                " ORDER BY acknowledged_at DESC, acknowledgment_id DESC LIMIT :limit OFFSET :offset",
                        params,
                        ACK_ROW_MAPPER
                )
        );

        int totalPages = totalItems == 0 ? 0 : (int) Math.ceil((double) totalItems / normalizedSize);
        return new PlatformAlertAcknowledgmentsPageResponse(items, normalizedPage, normalizedSize, totalItems, totalPages);
    }

    public ArrayList<PlatformAlertAcknowledgmentItem> getActiveAcknowledgments() {
        return new ArrayList<>(jdbcTemplate.query(
                "SELECT acknowledgment_id, alert_key, target_tenant_id, target_branch_id, note, " +
                        "acknowledged_by_user_id, acknowledged_by_user_name, acknowledged_at, expires_at, cleared_at, cleared_by_user_id, cleared_by_user_name, " +
                        "(cleared_at IS NULL AND (expires_at IS NULL OR expires_at > NOW())) AS active " +
                        "FROM public.platform_admin_alert_acknowledgments " +
                        "WHERE cleared_at IS NULL AND (expires_at IS NULL OR expires_at > NOW()) " +
                        "ORDER BY acknowledged_at DESC, acknowledgment_id DESC",
                ACK_ROW_MAPPER
        ));
    }

    public Set<String> getActiveAcknowledgedAlertKeysForGlobalScope() {
        List<String> keys = jdbcTemplate.query(
                "SELECT DISTINCT alert_key FROM public.platform_admin_alert_acknowledgments " +
                        "WHERE cleared_at IS NULL AND (expires_at IS NULL OR expires_at > NOW()) " +
                        "AND target_tenant_id IS NULL AND target_branch_id IS NULL",
                (rs, rowNum) -> rs.getString("alert_key")
        );
        return new HashSet<>(keys);
    }

    private PlatformAlertAcknowledgmentItem requireAcknowledgment(long acknowledgmentId) {
        List<PlatformAlertAcknowledgmentItem> results = jdbcTemplate.query(
                "SELECT acknowledgment_id, alert_key, target_tenant_id, target_branch_id, note, " +
                        "acknowledged_by_user_id, acknowledged_by_user_name, acknowledged_at, expires_at, cleared_at, cleared_by_user_id, cleared_by_user_name, " +
                        "(cleared_at IS NULL AND (expires_at IS NULL OR expires_at > NOW())) AS active " +
                        "FROM public.platform_admin_alert_acknowledgments WHERE acknowledgment_id = ?",
                ACK_ROW_MAPPER,
                acknowledgmentId
        );
        if (results.isEmpty()) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "PLATFORM_ALERT_ACK_NOT_FOUND",
                    "Platform alert acknowledgment not found"
            );
        }
        return results.get(0);
    }

    private String buildExactScopeClause(Integer targetTenantId,
                                         Integer targetBranchId,
                                         MapSqlParameterSource params) {
        StringBuilder where = new StringBuilder();
        if (targetTenantId == null) {
            where.append(" AND target_tenant_id IS NULL ");
        } else {
            params.addValue("targetTenantId", targetTenantId);
            where.append(" AND target_tenant_id = :targetTenantId ");
        }

        if (targetBranchId == null) {
            where.append(" AND target_branch_id IS NULL ");
        } else {
            params.addValue("targetBranchId", targetBranchId);
            where.append(" AND target_branch_id = :targetBranchId ");
        }
        return where.toString();
    }

    private String buildHistoryWhereClause(String alertKey,
                                           Integer targetTenantId,
                                           Integer targetBranchId,
                                           Boolean activeOnly,
                                           MapSqlParameterSource params) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1 ");
        if (alertKey != null && !alertKey.trim().isEmpty()) {
            params.addValue("alertKey", alertKey.trim().toLowerCase());
            where.append(" AND alert_key = :alertKey ");
        }
        if (targetTenantId != null) {
            params.addValue("targetTenantId", targetTenantId);
            where.append(" AND target_tenant_id = :targetTenantId ");
        }
        if (targetBranchId != null) {
            params.addValue("targetBranchId", targetBranchId);
            where.append(" AND target_branch_id = :targetBranchId ");
        }
        if (Boolean.TRUE.equals(activeOnly)) {
            where.append(" AND cleared_at IS NULL AND (expires_at IS NULL OR expires_at > NOW()) ");
        }
        return where.toString();
    }
}
