package com.example.valueinsoftbackend.ai.audit;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Repository
public class AiToolAuditRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AiToolAuditRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void create(UUID conversationId,
                       long companyId,
                       Long branchId,
                       long userId,
                       String toolName,
                       String inputJson,
                       String outputSummary,
                       boolean success,
                       String errorMessage,
                       Long durationMs) {
        jdbcTemplate.update("""
                INSERT INTO public.ai_tool_audit
                    (id, conversation_id, company_id, branch_id, user_id, tool_name, input_json,
                     output_summary, success, error_message, duration_ms, created_at)
                VALUES
                    (:id, :conversationId, :companyId, :branchId, :userId, :toolName, :inputJson,
                     :outputSummary, :success, :errorMessage, :durationMs, :createdAt)
                """, new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("conversationId", conversationId)
                .addValue("companyId", companyId)
                .addValue("branchId", branchId)
                .addValue("userId", userId)
                .addValue("toolName", toolName)
                .addValue("inputJson", inputJson)
                .addValue("outputSummary", outputSummary)
                .addValue("success", success)
                .addValue("errorMessage", errorMessage)
                .addValue("durationMs", durationMs)
                .addValue("createdAt", Timestamp.from(Instant.now())));
    }
}
