package com.example.valueinsoftbackend.customerbehavior.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class CustomerBehaviorAuditService {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public CustomerBehaviorAuditService(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void log(long companyId,
                    Long branchId,
                    long userId,
                    String eventType,
                    Object input,
                    boolean success,
                    long durationMs) {
        try {
            jdbcTemplate.update(
                    """
                            INSERT INTO public.customer_behavior_audit_log (
                                company_id, branch_id, user_id, event_type, input_json, success, duration_ms
                            ) VALUES (
                                :companyId, :branchId, :userId, :eventType, :inputJson, :success, :durationMs
                            )
                            """,
                    new MapSqlParameterSource()
                            .addValue("companyId", companyId)
                            .addValue("branchId", branchId)
                            .addValue("userId", userId)
                            .addValue("eventType", eventType)
                            .addValue("inputJson", safeJson(input))
                            .addValue("success", success)
                            .addValue("durationMs", durationMs)
            );
        } catch (RuntimeException exception) {
            log.warn("Failed to write customer behavior audit event companyId={} branchId={} eventType={}",
                    companyId, branchId, eventType, exception);
        }
    }

    private String safeJson(Object value) {
        if (value == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "{\"summary\":\"unserializable input\"}";
        }
    }
}
