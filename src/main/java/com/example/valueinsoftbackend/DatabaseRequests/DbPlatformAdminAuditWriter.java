package com.example.valueinsoftbackend.DatabaseRequests;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class DbPlatformAdminAuditWriter {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public DbPlatformAdminAuditWriter(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public String createAuditEvent(String actorUserName,
                                   String capabilityKey,
                                   String actionType,
                                   Integer targetTenantId,
                                   Integer targetBranchId,
                                   String requestSummaryJson,
                                   String contextSummaryJson,
                                   String resultStatus) {
        String correlationId = UUID.randomUUID().toString();
        namedParameterJdbcTemplate.update(
                "INSERT INTO public.platform_admin_audit_log " +
                        "(actor_user_id, actor_user_name, capability_key, action_type, target_tenant_id, target_branch_id, " +
                        "request_summary, context_summary, result_status, correlation_id) " +
                        "VALUES (" +
                        "(SELECT u.id FROM public.users u WHERE LOWER(u.\"userName\") = LOWER(:actorUserName) ORDER BY u.id ASC LIMIT 1), " +
                        ":actorUserName, :capabilityKey, :actionType, :targetTenantId, :targetBranchId, " +
                        "CAST(:requestSummaryJson AS jsonb), CAST(:contextSummaryJson AS jsonb), :resultStatus, :correlationId" +
                        ")",
                new MapSqlParameterSource()
                        .addValue("actorUserName", actorUserName)
                        .addValue("capabilityKey", capabilityKey)
                        .addValue("actionType", actionType)
                        .addValue("targetTenantId", targetTenantId)
                        .addValue("targetBranchId", targetBranchId)
                        .addValue("requestSummaryJson", safeJson(requestSummaryJson))
                        .addValue("contextSummaryJson", safeJson(contextSummaryJson))
                        .addValue("resultStatus", resultStatus)
                        .addValue("correlationId", correlationId)
        );
        return correlationId;
    }

    private String safeJson(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "{}";
        }
        return value;
    }
}
