package com.example.valueinsoftbackend.DatabaseRequests;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DbFinanceAudit {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public DbFinanceAudit(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public void createAuditEvent(int companyId,
                                 Integer branchId,
                                 Integer actorUserId,
                                 String eventType,
                                 String entityType,
                                 String entityId,
                                 String beforeStateJson,
                                 String afterStateJson,
                                 String reason,
                                 String correlationId) {
        namedParameterJdbcTemplate.update(
                "INSERT INTO public.finance_audit_event " +
                        "(company_id, branch_id, actor_user_id, event_type, entity_type, entity_id, " +
                        "before_state, after_state, reason, correlation_id) " +
                        "VALUES (:companyId, :branchId, :actorUserId, :eventType, :entityType, :entityId, " +
                        "CAST(:beforeState AS jsonb), CAST(:afterState AS jsonb), :reason, :correlationId)",
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("branchId", branchId)
                        .addValue("actorUserId", actorUserId)
                        .addValue("eventType", eventType)
                        .addValue("entityType", entityType)
                        .addValue("entityId", entityId)
                        .addValue("beforeState", beforeStateJson)
                        .addValue("afterState", afterStateJson)
                        .addValue("reason", reason)
                        .addValue("correlationId", correlationId));
    }
}
