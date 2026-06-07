package com.example.valueinsoftbackend.pricing.dynamic.repository;

import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PricingAuditRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PricingAuditRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void log(int companyId, Integer branchId, String eventType, String entityType,
                    String entityId, String actorName, String eventMessage, String payloadJson) {
        String sql = """
                INSERT INTO %s (
                    company_id, branch_id, event_type, entity_type, entity_id,
                    actor_name, event_message, payload_json, created_at
                ) VALUES (
                    :companyId, :branchId, :eventType, :entityType, :entityId,
                    :actorName, :eventMessage, CAST(:payloadJson AS jsonb), NOW()
                )
                """.formatted(TenantSqlIdentifiers.inventoryPricingAuditLogTable(companyId));

        jdbcTemplate.update(
                sql,
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("branchId", branchId)
                        .addValue("eventType", eventType)
                        .addValue("entityType", entityType)
                        .addValue("entityId", entityId)
                        .addValue("actorName", actorName)
                        .addValue("eventMessage", eventMessage)
                        .addValue("payloadJson", payloadJson == null || payloadJson.isBlank() ? "{}" : payloadJson)
        );
    }
}
