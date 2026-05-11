package com.example.valueinsoftbackend.ai.audit;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public class AiUsageLogRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AiUsageLogRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void create(long companyId,
                       long userId,
                       UUID conversationId,
                       String modelName,
                       int promptTokens,
                       int completionTokens,
                       int totalTokens,
                       BigDecimal estimatedCost,
                       Long durationMs) {
        jdbcTemplate.update("""
                INSERT INTO public.ai_usage_log
                    (id, company_id, user_id, conversation_id, model_name, prompt_tokens,
                     completion_tokens, total_tokens, estimated_cost, duration_ms, created_at)
                VALUES
                    (:id, :companyId, :userId, :conversationId, :modelName, :promptTokens,
                     :completionTokens, :totalTokens, :estimatedCost, :durationMs, :createdAt)
                """, new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("companyId", companyId)
                .addValue("userId", userId)
                .addValue("conversationId", conversationId)
                .addValue("modelName", modelName)
                .addValue("promptTokens", Math.max(0, promptTokens))
                .addValue("completionTokens", Math.max(0, completionTokens))
                .addValue("totalTokens", Math.max(0, totalTokens))
                .addValue("estimatedCost", estimatedCost == null ? BigDecimal.ZERO : estimatedCost)
                .addValue("durationMs", durationMs)
                .addValue("createdAt", Timestamp.from(Instant.now())));
    }

    public long countUserRequestsSince(long companyId, long userId, LocalDateTime since) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM public.ai_usage_log
                WHERE company_id = :companyId
                  AND user_id = :userId
                  AND created_at >= :since
                """, new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("userId", userId)
                .addValue("since", Timestamp.valueOf(since)), Long.class);
        return count == null ? 0L : count;
    }

    public long sumCompanyTokensSince(long companyId, LocalDateTime since) {
        Long total = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(total_tokens), 0)
                FROM public.ai_usage_log
                WHERE company_id = :companyId
                  AND created_at >= :since
                """, new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("since", Timestamp.valueOf(since)), Long.class);
        return total == null ? 0L : total;
    }
}
